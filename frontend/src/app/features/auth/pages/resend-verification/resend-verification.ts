import {Component, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {NotificationService} from '../../../../core/services/notification.service';
import {AuthService} from '../../../../core/services/auth.service';
import {EMAIL_REGEX} from '../../../../core/validation/patterns';
import {FormActions} from '../../../../shared/ui/form-actions/form-actions';
import {FormField} from '../../../../shared/ui/form-field/form-field';
import {PageIntro} from '../../../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../../../shared/ui/section-card/section-card';

@Component({
  selector: 'app-resend-verification',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    FormActions,
    FormField,
    PageIntro,
    SectionCard
  ],
  templateUrl: './resend-verification.html',
})
export class ResendVerification {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly i18nService = inject(I18nService);
  private readonly notificationService = inject(NotificationService);

  readonly submitting = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.pattern(EMAIL_REGEX)]]
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.authService.resendVerification(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.notificationService.success(this.i18nService.t('resendVerification.success'));
      },
      error: (error) => {
        this.submitting.set(false);
        this.notificationService.error(error?.error?.message ?? this.i18nService.t('resendVerification.error'));
      }
    });
  }

  fieldError(): string {
    const control = this.form.controls.email;

    if (!control.touched || !control.invalid) {
      return '';
    }

    if (control.hasError('required')) {
      return this.i18nService.t('validation.required');
    }

    if (control.hasError('pattern')) {
      return this.i18nService.t('validation.email');
    }

    return this.i18nService.t('validation.invalid');
  }
}
