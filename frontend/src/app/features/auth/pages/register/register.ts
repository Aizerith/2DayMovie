import {Component, inject, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router, RouterLink} from '@angular/router';
import {TranslocoPipe} from '@jsverse/transloco';
import {AuthService} from '../../../../core/services/auth.service';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {NotificationService} from '../../../../core/services/notification.service';
import {EMAIL_REGEX, PASSWORD_REGEX} from '../../../../core/validation/patterns';
import {FormActions} from '../../../../shared/ui/form-actions/form-actions';
import {FormField} from '../../../../shared/ui/form-field/form-field';
import {PageIntro} from '../../../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../../../shared/ui/section-card/section-card';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    TranslocoPipe,
    FormActions,
    FormField,
    PageIntro,
    SectionCard
  ],
  templateUrl: './register.html',
})
export class Register {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly i18nService = inject(I18nService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.pattern(EMAIL_REGEX)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    password: ['', [Validators.required, Validators.pattern(PASSWORD_REGEX)]],
    confirmPassword: ['', [Validators.required]]
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.form.controls.password.value !== this.form.controls.confirmPassword.value) {
      this.notificationService.error(this.i18nService.t('register.passwordMismatch'));
      return;
    }

    this.submitting.set(true);
    this.authService.register({
      email: this.form.controls.email.value,
      firstName: this.form.controls.firstName.value,
      lastName: this.form.controls.lastName.value,
      password: this.form.controls.password.value
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.notificationService.success(this.i18nService.t('register.success'));
        void this.router.navigate(['/login']);
      },
      error: (error) => {
        this.submitting.set(false);
        this.notificationService.error(error?.error?.message ?? this.i18nService.t('register.error'));
      }
    });
  }

  resetForm(): void {
    this.form.reset({
      email: '',
      firstName: '',
      lastName: '',
      password: '',
      confirmPassword: ''
    });
  }

  fieldError(fieldName: 'email' | 'firstName' | 'lastName' | 'password' | 'confirmPassword'): string {
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
}
