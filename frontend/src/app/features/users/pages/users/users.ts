import {Component, computed, inject, signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslocoPipe} from '@jsverse/transloco';
import {DataList, DataListColumn, DataListRow, DataListSortChange} from '../../../../shared/ui/data-list/data-list';
import {EmptyState} from '../../../../shared/ui/empty-state/empty-state';
import {FormActions} from '../../../../shared/ui/form-actions/form-actions';
import {FormField} from '../../../../shared/ui/form-field/form-field';
import {ModalConfirm} from '../../../../shared/ui/modal-confirm/modal-confirm';
import {NoticeBanner} from '../../../../shared/ui/notice-banner/notice-banner';
import {PageIntro} from '../../../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../../../shared/ui/section-card/section-card';
import {Toolbar} from '../../../../shared/ui/toolbar/toolbar';
import {CreateUserRequest, UpdateUserRequest, UserAdmin} from '../../../../core/models/user.model';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {UsersService} from '../../../../core/services/users.service';
import {EMAIL_REGEX, PASSWORD_REGEX} from '../../../../core/validation/patterns';

@Component({
  selector: 'app-users',
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
  templateUrl: './users.html',
})
export class Users {
  private readonly usersService = inject(UsersService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly i18nService = inject(I18nService);

  readonly users = signal<UserAdmin[]>([]);
  readonly selectedUserId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly infoMessage = signal('');
  readonly deleteModalOpen = signal(false);
  readonly page = signal(0);
  readonly pageSize = signal(5);
  readonly totalItems = signal(0);
  readonly sort = signal('createdAt,desc');

  readonly columns = computed<DataListColumn[]>(() => {
    this.i18nService.activeLang();

    return [
      {key: 'email', label: this.i18nService.t('common.email')},
      {key: 'fullName', label: this.i18nService.t('users.columns.name'), sortable: false},
      {key: 'role', label: this.i18nService.t('common.role')},
      {key: 'status', label: this.i18nService.t('users.columns.status'), sortKey: 'enabled'},
      {key: 'verified', label: this.i18nService.t('users.columns.verified'), sortKey: 'emailVerified'}
    ];
  });

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.pattern(EMAIL_REGEX)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    password: ['', [Validators.pattern(PASSWORD_REGEX)]],
    role: ['USER', [Validators.required]],
    enabled: [true]
  });

  constructor() {
    this.loadUsers();
  }

  get selectedUser(): UserAdmin | null {
    return this.users().find(user => user.id === this.selectedUserId()) ?? null;
  }

  get rows(): DataListRow[] {
    this.i18nService.activeLang();

    return this.users().map(user => ({
      id: String(user.id),
      email: user.email,
      fullName: `${user.firstName} ${user.lastName}`,
      role: user.role,
      status: user.enabled
        ? this.i18nService.t('enums.userStatus.enabled')
        : this.i18nService.t('enums.userStatus.disabled'),
      verified: user.emailVerified
        ? this.i18nService.t('users.verified.verified')
        : this.i18nService.t('users.verified.pending')
    }));
  }

  get selectedRowKey(): string {
    return this.selectedUserId() !== null ? String(this.selectedUserId()) : '';
  }

  fieldError(fieldName: 'email' | 'firstName' | 'lastName' | 'password' | 'role' | 'enabled'): string {
    const control = this.form.controls[fieldName];

    if (!control.touched || !control.invalid) {
      return '';
    }

    if (control.hasError('required')) {
      return this.i18nService.t('validation.required');
    }

    if (control.hasError('pattern') && fieldName === 'email') {
      return this.i18nService.t('validation.email');
    }

    if (control.hasError('pattern') && fieldName === 'password') {
      return this.i18nService.t('validation.passwordPattern');
    }

    if (control.hasError('maxlength')) {
      return this.i18nService.t('validation.maxlength', {
        value: control.getError('maxlength').requiredLength
      });
    }

    return this.i18nService.t('validation.invalid');
  }

  loadUsers(): void {
    this.loading.set(true);
    this.usersService.findPage({page: this.page(), size: this.pageSize(), sort: this.sort()}).subscribe({
      next: (response) => {
        this.users.set(response.items);
        this.totalItems.set(response.totalItems);
        if (this.selectedUserId() && !response.items.some(user => user.id === this.selectedUserId())) {
          this.resetForm();
        }
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('users.errors.load')));
        this.loading.set(false);
      }
    });
  }

  updatePage(page: number): void {
    this.page.set(page);
    this.loadUsers();
  }

  updatePageSize(size: number): void {
    this.pageSize.set(size);
    this.page.set(0);
    this.loadUsers();
  }

  updateSort(change: DataListSortChange): void {
    this.sort.set(`${change.key},${change.direction}`);
    this.page.set(0);
    this.loadUsers();
  }

  selectRow(row: DataListRow): void {
    const userId = Number(row['id']);
    const user = this.users().find(item => item.id === userId);

    if (!user) {
      return;
    }

    this.selectedUserId.set(user.id);
    this.errorMessage.set('');
    this.infoMessage.set('');
    this.form.setValue({
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      password: '',
      role: user.role,
      enabled: user.enabled
    });
  }

  startCreate(): void {
    this.resetForm();
    this.infoMessage.set(this.i18nService.t('users.modeCreate'));
  }

  submit(): void {
    if (!this.selectedUser && !this.form.controls.password.value.trim().length) {
      this.form.controls.password.setErrors({required: true});
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.errorMessage.set('');
    this.infoMessage.set('');

    const selectedUser = this.selectedUser;

    if (selectedUser) {
      const payload: UpdateUserRequest = {
        email: this.form.getRawValue().email,
        firstName: this.form.getRawValue().firstName,
        lastName: this.form.getRawValue().lastName,
        password: this.form.getRawValue().password || undefined,
        role: this.form.getRawValue().role,
        enabled: this.form.getRawValue().enabled
      };

      this.usersService.update(selectedUser.id, payload).subscribe({
        next: (user) => {
          this.replaceUser(user);
          this.selectedUserId.set(user.id);
          this.form.patchValue({password: ''});
          this.infoMessage.set(this.i18nService.t('users.updated'));
          this.saving.set(false);
        },
        error: (error) => {
          this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('users.errors.update')));
          this.saving.set(false);
        }
      });
      return;
    }

    const payload: CreateUserRequest = {
      email: this.form.getRawValue().email,
      firstName: this.form.getRawValue().firstName,
      lastName: this.form.getRawValue().lastName,
      password: this.form.getRawValue().password,
      role: this.form.getRawValue().role,
      enabled: this.form.getRawValue().enabled
    };

    this.usersService.create(payload).subscribe({
      next: (user) => {
        this.users.set([...this.users(), user]);
        this.selectedUserId.set(user.id);
        this.form.patchValue({password: ''});
        this.infoMessage.set(this.i18nService.t('users.created'));
        this.saving.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('users.errors.create')));
        this.saving.set(false);
      }
    });
  }

  askDelete(): void {
    if (!this.selectedUser) {
      return;
    }

    this.deleteModalOpen.set(true);
  }

  confirmDelete(): void {
    const selectedUser = this.selectedUser;

    if (!selectedUser) {
      this.deleteModalOpen.set(false);
      return;
    }

    this.usersService.delete(selectedUser.id).subscribe({
      next: () => {
        this.users.set(this.users().filter(user => user.id !== selectedUser.id));
        this.resetForm();
        this.deleteModalOpen.set(false);
        this.infoMessage.set(this.i18nService.t('users.deleted'));
      },
      error: (error) => {
        this.deleteModalOpen.set(false);
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('users.errors.delete')));
      }
    });
  }

  cancelDelete(): void {
    this.deleteModalOpen.set(false);
  }

  resetForm(): void {
    this.selectedUserId.set(null);
    this.form.reset({
      email: '',
      firstName: '',
      lastName: '',
      password: '',
      role: 'USER',
      enabled: true
    });
  }

  private replaceUser(updatedUser: UserAdmin): void {
    this.users.set(this.users().map(user => user.id === updatedUser.id ? updatedUser : user));
  }

  private toErrorMessage(error: unknown, fallbackMessage: string): string {
    if (error instanceof HttpErrorResponse) {
      if (typeof error.error?.message === 'string' && error.error.message.trim().length) {
        return error.error.message;
      }

      if (error.status === 401) {
        return this.i18nService.t('users.errors.session');
      }

      if (error.status === 403) {
        return this.i18nService.t('users.errors.forbidden');
      }

      if (error.status === 0) {
        return this.i18nService.t('users.errors.backend');
      }
    }

    return fallbackMessage;
  }
}
