import {Component, computed, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {AuthService} from '../../../../core/services/auth.service';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {NotificationService} from '../../../../core/services/notification.service';
import {PASSWORD_REGEX} from '../../../../core/validation/patterns';
import {FormActions} from '../../../../shared/ui/form-actions/form-actions';
import {FormField} from '../../../../shared/ui/form-field/form-field';
import {NoticeBanner} from '../../../../shared/ui/notice-banner/notice-banner';
import {PageIntro} from '../../../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../../../shared/ui/section-card/section-card';

@Component({
  selector: 'app-reset-password',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    FormActions,
    FormField,
    NoticeBanner,
    PageIntro,
    SectionCard
  ],
  templateUrl: './reset-password.html',
})
export class ResetPassword {
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly i18nService = inject(I18nService);
  private readonly notificationService = inject(NotificationService);

  readonly submitting = signal(false);
  readonly token = signal(this.route.snapshot.queryParamMap.get('token') ?? '');
  readonly tokenMissing = computed(() => !this.token().trim().length);

  readonly form = this.formBuilder.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.pattern(PASSWORD_REGEX)]],
    confirmPassword: ['', [Validators.required]]
  });

  submit(): void {
    if (this.tokenMissing()) {
      this.notificationService.error(this.i18nService.t('resetPassword.missingToken'));
      return;
    }

    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.form.controls.newPassword.value !== this.form.controls.confirmPassword.value) {
      this.notificationService.error(this.i18nService.t('resetPassword.passwordMismatch'));
      return;
    }

    this.submitting.set(true);
    this.authService.resetPassword({
      token: this.token(),
      newPassword: this.form.controls.newPassword.value
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.notificationService.success(this.i18nService.t('resetPassword.success'));
        void this.router.navigate(['/login']);
      },
      error: (error) => {
        this.submitting.set(false);
        this.notificationService.error(error?.error?.message ?? this.i18nService.t('resetPassword.error'));
      }
    });
  }

  fieldError(fieldName: 'newPassword' | 'confirmPassword'): string {
    const control = this.form.controls[fieldName];

    if (!control.touched || !control.invalid) {
      return '';
    }

    if (control.hasError('required')) {
      return this.i18nService.t('validation.required');
    }

    if (control.hasError('pattern')) {
      return this.i18nService.t('validation.passwordPattern');
    }

    return this.i18nService.t('validation.invalid');
  }
}
