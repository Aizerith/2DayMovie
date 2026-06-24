import {Component, inject, input, output} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {I18nService} from '../../../core/i18n/i18n.service';

@Component({
  selector: 'app-modal-confirm',
  imports: [TranslocoPipe],
  templateUrl: './modal-confirm.html',
})
export class ModalConfirm {
  private readonly i18nService = inject(I18nService);

  readonly open = input(false);
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input('');
  readonly cancelLabel = input('');
  readonly tone = input<'error' | 'warning' | 'primary'>('primary');

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  resolvedConfirmLabel(): string {
    return this.confirmLabel() || this.i18nService.t('common.confirm');
  }

  resolvedCancelLabel(): string {
    return this.cancelLabel() || this.i18nService.t('common.cancel');
  }
}
