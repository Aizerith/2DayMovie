import {Component, inject, input, output} from '@angular/core';
import {I18nService} from '../../../core/i18n/i18n.service';

@Component({
  selector: 'app-form-actions',
  templateUrl: './form-actions.html',
})
export class FormActions {
  private readonly i18nService = inject(I18nService);

  readonly submitLabel = input('');
  readonly submitTestId = input('');
  readonly cancelLabel = input('');
  readonly disabled = input(false);
  readonly submitting = input(false);
  readonly showCancel = input(true);

  readonly cancelled = output<void>();

  resolvedSubmitLabel(): string {
    return this.submitLabel() || this.i18nService.t('common.save');
  }

  resolvedCancelLabel(): string {
    return this.cancelLabel() || this.i18nService.t('common.cancel');
  }

  busyLabel(): string {
    return this.i18nService.t('common.saving');
  }
}
