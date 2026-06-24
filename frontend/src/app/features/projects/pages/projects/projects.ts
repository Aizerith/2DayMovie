import {Component, computed, inject, signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslocoPipe} from '@jsverse/transloco';
import {AuthService} from '../../../../core/services/auth.service';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {CreateProjectRequest, Project, ProjectStatus, UpdateProjectRequest} from '../../../../core/models/project.model';
import {ProjectsService} from '../../../../core/services/projects.service';
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
  selector: 'app-projects',
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
  templateUrl: './projects.html',
})
export class Projects {
  private readonly projectsService = inject(ProjectsService);
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly i18nService = inject(I18nService);

  readonly projects = signal<Project[]>([]);
  readonly selectedProjectId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly infoMessage = signal('');
  readonly deleteModalOpen = signal(false);
  readonly page = signal(0);
  readonly pageSize = signal(5);
  readonly totalItems = signal(0);
  readonly sort = signal('updatedAt,desc');
  readonly currentUser = this.authService.currentUser;
  readonly isAdmin = computed(() => this.authService.getCurrentUserRole() === 'ADMIN');
  readonly statusOptions: ProjectStatus[] = ['DRAFT', 'ACTIVE', 'ARCHIVED'];

  readonly columns = computed<DataListColumn[]>(() => {
    this.i18nService.activeLang();

    return [
      {key: 'name', label: this.i18nService.t('projects.columns.project')},
      {key: 'status', label: this.i18nService.t('projects.columns.status')},
      {key: 'owner', label: this.i18nService.t('projects.columns.owner'), sortable: false},
      {key: 'updatedAt', label: this.i18nService.t('projects.columns.updatedAt')}
    ];
  });

  readonly form = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.maxLength(1000)]],
    status: ['DRAFT' as ProjectStatus, [Validators.required]]
  });

  constructor() {
    this.loadProjects();
  }

  get selectedProject(): Project | null {
    return this.projects().find(project => project.id === this.selectedProjectId()) ?? null;
  }

  get selectedRowKey(): string {
    return this.selectedProjectId() !== null ? String(this.selectedProjectId()) : '';
  }

  get rows(): DataListRow[] {
    this.i18nService.activeLang();

    return this.projects().map(project => ({
      id: String(project.id),
      name: project.name,
      status: this.statusLabel(project.status),
      owner: project.ownerName,
      updatedAt: this.formatDate(project.updatedAt)
    }));
  }

  fieldError(fieldName: 'name' | 'description' | 'status'): string {
    const control = this.form.controls[fieldName];

    if (!control.touched || !control.invalid) {
      return '';
    }

    if (control.hasError('required')) {
      return this.i18nService.t('validation.required');
    }

    if (control.hasError('maxlength')) {
      return this.i18nService.t('validation.maxlength', {
        value: control.getError('maxlength').requiredLength
      });
    }

    return this.i18nService.t('validation.invalid');
  }

  loadProjects(): void {
    this.loading.set(true);
    this.projectsService.findPage({page: this.page(), size: this.pageSize(), sort: this.sort()}).subscribe({
      next: (response) => {
        this.projects.set(response.items);
        this.totalItems.set(response.totalItems);
        if (this.selectedProjectId() && !response.items.some(project => project.id === this.selectedProjectId())) {
          this.resetForm();
        }
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('projects.errors.load')));
        this.loading.set(false);
      }
    });
  }

  updatePage(page: number): void {
    this.page.set(page);
    this.loadProjects();
  }

  updatePageSize(size: number): void {
    this.pageSize.set(size);
    this.page.set(0);
    this.loadProjects();
  }

  updateSort(change: DataListSortChange): void {
    this.sort.set(`${change.key},${change.direction}`);
    this.page.set(0);
    this.loadProjects();
  }

  selectRow(row: DataListRow): void {
    const projectId = Number(row['id']);
    const project = this.projects().find(item => item.id === projectId);

    if (!project) {
      return;
    }

    this.selectedProjectId.set(project.id);
    this.errorMessage.set('');
    this.infoMessage.set('');
    this.form.setValue({
      name: project.name,
      description: project.description,
      status: project.status
    });
  }

  startCreate(): void {
    this.resetForm();
    this.infoMessage.set(this.i18nService.t('projects.modeCreate'));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.errorMessage.set('');
    this.infoMessage.set('');

    const payload = this.form.getRawValue();
    const selectedProject = this.selectedProject;

    if (selectedProject) {
      const updatePayload: UpdateProjectRequest = payload;
      this.projectsService.update(selectedProject.id, updatePayload).subscribe({
        next: (project) => {
          this.projects.set(this.projects().map(item => item.id === project.id ? project : item));
          this.selectedProjectId.set(project.id);
          this.infoMessage.set(this.i18nService.t('projects.updated'));
          this.saving.set(false);
        },
        error: (error) => {
          this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('projects.errors.update')));
          this.saving.set(false);
        }
      });
      return;
    }

    const createPayload: CreateProjectRequest = payload;
    this.projectsService.create(createPayload).subscribe({
      next: (project) => {
        this.projects.set([project, ...this.projects()]);
        this.selectedProjectId.set(project.id);
        this.infoMessage.set(this.i18nService.t('projects.created'));
        this.saving.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('projects.errors.create')));
        this.saving.set(false);
      }
    });
  }

  askDelete(): void {
    if (!this.selectedProject) {
      return;
    }

    this.deleteModalOpen.set(true);
  }

  confirmDelete(): void {
    const selectedProject = this.selectedProject;

    if (!selectedProject) {
      this.deleteModalOpen.set(false);
      return;
    }

    this.projectsService.delete(selectedProject.id).subscribe({
      next: () => {
        this.projects.set(this.projects().filter(project => project.id !== selectedProject.id));
        this.resetForm();
        this.deleteModalOpen.set(false);
        this.infoMessage.set(this.i18nService.t('projects.deleted'));
      },
      error: (error) => {
        this.deleteModalOpen.set(false);
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('projects.errors.delete')));
      }
    });
  }

  cancelDelete(): void {
    this.deleteModalOpen.set(false);
  }

  resetForm(): void {
    this.selectedProjectId.set(null);
    this.form.reset({
      name: '',
      description: '',
      status: 'DRAFT'
    });
  }

  statusLabel(status: ProjectStatus): string {
    switch (status) {
      case 'ACTIVE':
        return this.i18nService.t('enums.projectStatus.ACTIVE');
      case 'ARCHIVED':
        return this.i18nService.t('enums.projectStatus.ARCHIVED');
      default:
        return this.i18nService.t('enums.projectStatus.DRAFT');
    }
  }

  private formatDate(value: string): string {
    return new Date(value).toLocaleString(this.i18nService.locale(), {
      dateStyle: 'short',
      timeStyle: 'short'
    });
  }

  private toErrorMessage(error: unknown, fallbackMessage: string): string {
    if (error instanceof HttpErrorResponse) {
      if (typeof error.error?.message === 'string' && error.error.message.trim().length) {
        return error.error.message;
      }

      if (error.status === 401) {
        return this.i18nService.t('projects.errors.session');
      }

      if (error.status === 403) {
        return this.i18nService.t('projects.errors.forbidden');
      }

      if (error.status === 0) {
        return this.i18nService.t('projects.errors.backend');
      }
    }

    return fallbackMessage;
  }
}
