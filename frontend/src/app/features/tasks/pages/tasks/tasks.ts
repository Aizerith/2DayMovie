import {Component, computed, DestroyRef, inject, signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {TranslocoPipe} from '@jsverse/transloco';
import {Project} from '../../../../core/models/project.model';
import {CreateTaskRequest, Task, TaskPriority, TaskStatus, UpdateTaskRequest} from '../../../../core/models/task.model';
import {AssignableUser} from '../../../../core/models/user.model';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {UsersService} from '../../../../core/services/users.service';
import {ProjectsService} from '../../../../core/services/projects.service';
import {RealtimeService} from '../../../../core/services/realtime.service';
import {TasksService} from '../../../../core/services/tasks.service';
import {DataList, DataListColumn, DataListRow, DataListSortChange} from '../../../../shared/ui/data-list/data-list';
import {EmptyState} from '../../../../shared/ui/empty-state/empty-state';
import {FormActions} from '../../../../shared/ui/form-actions/form-actions';
import {FormField} from '../../../../shared/ui/form-field/form-field';
import {ModalConfirm} from '../../../../shared/ui/modal-confirm/modal-confirm';
import {NoticeBanner} from '../../../../shared/ui/notice-banner/notice-banner';
import {PageIntro} from '../../../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../../../shared/ui/section-card/section-card';
import {Toolbar} from '../../../../shared/ui/toolbar/toolbar';

@Component({
  selector: 'app-tasks',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    DataList,
    EmptyState,
    FormActions,
    FormField,
    ModalConfirm,
    NoticeBanner,
    PageIntro,
    SectionCard,
    Toolbar
  ],
  templateUrl: './tasks.html',
})
export class Tasks {
  private readonly tasksService = inject(TasksService);
  private readonly projectsService = inject(ProjectsService);
  private readonly usersService = inject(UsersService);
  private readonly realtimeService = inject(RealtimeService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly i18nService = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);

  readonly tasks = signal<Task[]>([]);
  readonly projects = signal<Project[]>([]);
  readonly assignableUsers = signal<AssignableUser[]>([]);
  readonly selectedTaskId = signal<number | null>(null);
  readonly selectedProjectFilter = signal<number | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly infoMessage = signal('');
  readonly deleteModalOpen = signal(false);
  readonly page = signal(0);
  readonly pageSize = signal(5);
  readonly totalItems = signal(0);
  readonly sort = signal('dueDate,asc');
  readonly statusOptions: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];
  readonly priorityOptions: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH'];
  readonly columns = computed<DataListColumn[]>(() => {
    this.i18nService.activeLang();

    return [
      {key: 'title', label: this.i18nService.t('tasks.columns.title')},
      {key: 'projectName', label: this.i18nService.t('tasks.columns.project'), sortable: false},
      {key: 'assigneeName', label: this.i18nService.t('tasks.columns.assignee'), sortable: false},
      {key: 'status', label: this.i18nService.t('tasks.columns.status')},
      {key: 'priority', label: this.i18nService.t('tasks.columns.priority')},
      {key: 'dueDate', label: this.i18nService.t('tasks.columns.dueDate')}
    ];
  });

  readonly form = this.formBuilder.nonNullable.group({
    projectId: [0, [Validators.required, Validators.min(1)]],
    assigneeId: [0],
    title: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.maxLength(1000)]],
    status: ['TODO' as TaskStatus, [Validators.required]],
    priority: ['MEDIUM' as TaskPriority, [Validators.required]],
    dueDate: ['']
  });

  readonly availableProjectOptions = computed(() => this.projects().map(project => ({
    id: project.id,
    name: project.name
  })));

  readonly availableAssigneeOptions = computed(() => this.assignableUsers().map(user => ({
    id: user.id,
    name: `${user.fullName} (${user.role})`
  })));

  constructor() {
    this.realtimeService.notifications$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((notification) => {
        if (notification.type.startsWith('TASK_')) {
          this.loadTasks();
        }
      });

    this.loadAssignableUsers();
    this.loadProjects();
    this.loadTasks();
  }

  get selectedTaskLocked(): boolean {
    return !!this.selectedTask && !this.selectedTask.manageable;
  }

  get selectedTask(): Task | null {
    return this.tasks().find(task => task.id === this.selectedTaskId()) ?? null;
  }

  get selectedRowKey(): string {
    return this.selectedTaskId() !== null ? String(this.selectedTaskId()) : '';
  }

  get rows(): DataListRow[] {
    this.i18nService.activeLang();

    return this.tasks().map(task => ({
      id: String(task.id),
      title: task.title,
      projectName: task.projectName,
      assigneeName: task.assigneeName ?? this.i18nService.t('common.notAssigned'),
      status: this.statusLabel(task.status),
      priority: this.priorityLabel(task.priority),
      dueDate: task.dueDate ? this.formatDate(task.dueDate) : this.i18nService.t('common.notDefined')
    }));
  }

  loadProjects(): void {
    this.projectsService.findAll().subscribe({
      next: (projects) => {
        this.projects.set(projects);
        if (!this.selectedTask && projects.length === 1) {
          this.form.patchValue({projectId: projects[0].id});
        }
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('tasks.errors.loadProjects')));
      }
    });
  }

  loadAssignableUsers(): void {
    this.usersService.findAssignable().subscribe({
      next: (users) => this.assignableUsers.set(users),
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('tasks.errors.loadUsers')));
      }
    });
  }

  loadTasks(): void {
    this.loading.set(true);
    this.tasksService.findPage(
      {page: this.page(), size: this.pageSize(), sort: this.sort()},
      this.selectedProjectFilter()
    ).subscribe({
      next: (response) => {
        this.tasks.set(response.items);
        this.totalItems.set(response.totalItems);
        if (this.selectedTaskId() && !response.items.some(task => task.id === this.selectedTaskId())) {
          this.resetForm();
        }
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('tasks.errors.loadTasks')));
        this.loading.set(false);
      }
    });
  }

  updateProjectFilter(value: string): void {
    const projectId = Number(value);
    this.selectedProjectFilter.set(projectId > 0 ? projectId : null);
    this.page.set(0);
    this.loadTasks();
  }

  updatePage(page: number): void {
    this.page.set(page);
    this.loadTasks();
  }

  updatePageSize(size: number): void {
    this.pageSize.set(size);
    this.page.set(0);
    this.loadTasks();
  }

  updateSort(change: DataListSortChange): void {
    this.sort.set(`${change.key},${change.direction}`);
    this.page.set(0);
    this.loadTasks();
  }

  selectRow(row: DataListRow): void {
    const taskId = Number(row['id']);
    const task = this.tasks().find(item => item.id === taskId);

    if (!task) {
      return;
    }

    this.selectedTaskId.set(task.id);
    this.errorMessage.set('');
    this.infoMessage.set('');
    this.form.setValue({
      projectId: task.projectId,
      assigneeId: task.assigneeId ?? 0,
      title: task.title,
      description: task.description,
      status: task.status,
      priority: task.priority,
      dueDate: task.dueDate ?? ''
    });
  }

  startCreate(): void {
    this.resetForm();
    this.infoMessage.set(this.i18nService.t('tasks.modeCreate'));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.selectedTaskLocked) {
      this.errorMessage.set(this.i18nService.t('tasks.lockedError'));
      return;
    }

    this.saving.set(true);
    this.errorMessage.set('');
    this.infoMessage.set('');

    const payload = this.normalizePayload();
    const selectedTask = this.selectedTask;

    if (selectedTask) {
      this.tasksService.update(selectedTask.id, payload).subscribe({
        next: (task) => {
          this.tasks.set(this.tasks().map(item => item.id === task.id ? task : item));
          this.selectedTaskId.set(task.id);
          this.infoMessage.set(this.i18nService.t('tasks.updated'));
          this.saving.set(false);
        },
        error: (error) => {
          this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('tasks.errors.update')));
          this.saving.set(false);
        }
      });
      return;
    }

    this.tasksService.create(payload).subscribe({
      next: (task) => {
        this.tasks.set([task, ...this.tasks()]);
        this.selectedTaskId.set(task.id);
        this.infoMessage.set(this.i18nService.t('tasks.created'));
        this.saving.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('tasks.errors.create')));
        this.saving.set(false);
      }
    });
  }

  askDelete(): void {
    if (!this.selectedTask) {
      return;
    }

    this.deleteModalOpen.set(true);
  }

  confirmDelete(): void {
    const selectedTask = this.selectedTask;

    if (!selectedTask) {
      this.deleteModalOpen.set(false);
      return;
    }

    this.tasksService.delete(selectedTask.id).subscribe({
      next: () => {
        this.tasks.set(this.tasks().filter(task => task.id !== selectedTask.id));
        this.resetForm();
        this.deleteModalOpen.set(false);
        this.infoMessage.set(this.i18nService.t('tasks.deleted'));
      },
      error: (error) => {
        this.deleteModalOpen.set(false);
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('tasks.errors.delete')));
      }
    });
  }

  cancelDelete(): void {
    this.deleteModalOpen.set(false);
  }

  resetForm(): void {
    this.selectedTaskId.set(null);
    this.form.reset({
      projectId: this.projects().length === 1 ? this.projects()[0].id : 0,
      assigneeId: 0,
      title: '',
      description: '',
      status: 'TODO',
      priority: 'MEDIUM',
      dueDate: ''
    });
  }

  statusLabel(status: TaskStatus): string {
    switch (status) {
      case 'IN_PROGRESS':
        return this.i18nService.t('enums.taskStatus.IN_PROGRESS');
      case 'DONE':
        return this.i18nService.t('enums.taskStatus.DONE');
      default:
        return this.i18nService.t('enums.taskStatus.TODO');
    }
  }

  priorityLabel(priority: TaskPriority): string {
    switch (priority) {
      case 'HIGH':
        return this.i18nService.t('enums.taskPriority.HIGH');
      case 'LOW':
        return this.i18nService.t('enums.taskPriority.LOW');
      default:
        return this.i18nService.t('enums.taskPriority.MEDIUM');
    }
  }

  fieldError(fieldName: 'projectId' | 'assigneeId' | 'title' | 'description' | 'status' | 'priority' | 'dueDate'): string {
    const control = this.form.controls[fieldName];

    if (!control.touched || !control.invalid) {
      return '';
    }

    if (control.hasError('required')) {
      return this.i18nService.t('validation.required');
    }

    if (control.hasError('min')) {
      return this.i18nService.t('validation.selectProject');
    }

    if (control.hasError('maxlength')) {
      return this.i18nService.t('validation.maxlength', {
        value: control.getError('maxlength').requiredLength
      });
    }

    return this.i18nService.t('validation.invalid');
  }

  private normalizePayload(): CreateTaskRequest | UpdateTaskRequest {
    const rawValue = this.form.getRawValue();

    return {
      projectId: rawValue.projectId,
      assigneeId: rawValue.assigneeId > 0 ? rawValue.assigneeId : null,
      title: rawValue.title.trim(),
      description: rawValue.description.trim(),
      status: rawValue.status,
      priority: rawValue.priority,
      dueDate: rawValue.dueDate || null
    };
  }

  private formatDate(value: string): string {
    return new Date(value).toLocaleDateString(this.i18nService.locale());
  }

  private toErrorMessage(error: unknown, fallbackMessage: string): string {
    if (error instanceof HttpErrorResponse) {
      if (typeof error.error?.message === 'string' && error.error.message.trim().length) {
        return error.error.message;
      }

      if (error.status === 401) {
        return this.i18nService.t('tasks.errors.session');
      }

      if (error.status === 403) {
        return this.i18nService.t('tasks.errors.forbidden');
      }

      if (error.status === 0) {
        return this.i18nService.t('tasks.errors.backend');
      }
    }

    return fallbackMessage;
  }
}
