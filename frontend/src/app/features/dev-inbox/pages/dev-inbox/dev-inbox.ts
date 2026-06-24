import {Component, computed, inject, signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {TranslocoPipe} from '@jsverse/transloco';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {DevMail} from '../../../../core/models/dev-mail.model';
import {DevMailsService} from '../../../../core/services/dev-mails.service';
import {DataList, DataListColumn, DataListRow} from '../../../../shared/ui/data-list/data-list';
import {EmptyState} from '../../../../shared/ui/empty-state/empty-state';
import {NoticeBanner} from '../../../../shared/ui/notice-banner/notice-banner';
import {PageIntro} from '../../../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../../../shared/ui/section-card/section-card';
import {Toolbar} from '../../../../shared/ui/toolbar/toolbar';

@Component({
  selector: 'app-dev-inbox',
  imports: [
    TranslocoPipe,
    DataList,
    EmptyState,
    NoticeBanner,
    PageIntro,
    SectionCard,
    Toolbar
  ],
  templateUrl: './dev-inbox.html',
})
export class DevInbox {
  private readonly devMailsService = inject(DevMailsService);
  private readonly i18nService = inject(I18nService);

  readonly mails = signal<DevMail[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly infoMessage = signal('');
  readonly selectedMailId = signal<number | null>(null);

  readonly columns = computed<DataListColumn[]>(() => {
    this.i18nService.activeLang();

    return [
      {key: 'subject', label: this.i18nService.t('devInbox.columns.subject')},
      {key: 'to', label: this.i18nService.t('devInbox.columns.to')},
      {key: 'createdAt', label: this.i18nService.t('devInbox.columns.createdAt')}
    ];
  });

  constructor() {
    this.loadMails();
  }

  get selectedMail(): DevMail | null {
    return this.mails().find(mail => mail.id === this.selectedMailId()) ?? null;
  }

  get selectedRowKey(): string {
    return this.selectedMailId() !== null ? String(this.selectedMailId()) : '';
  }

  get rows(): DataListRow[] {
    this.i18nService.activeLang();

    return this.mails().map(mail => ({
      id: String(mail.id),
      subject: mail.subject,
      to: mail.to,
      createdAt: this.formatDate(mail.createdAt)
    }));
  }

  get resetLink(): string {
    const selectedMail = this.selectedMail;
    if (!selectedMail) {
      return '';
    }

    const match = selectedMail.body.match(/https?:\/\/\S+/);
    return match?.[0] ?? '';
  }

  get selectedMailCreatedAt(): string {
    return this.selectedMail ? this.formatDate(this.selectedMail.createdAt) : '';
  }

  loadMails(): void {
    this.loading.set(true);
    this.errorMessage.set('');
    this.infoMessage.set('');

    this.devMailsService.findAll().subscribe({
      next: (mails) => {
        this.mails.set(mails);
        if (this.selectedMailId() && !mails.some(mail => mail.id === this.selectedMailId())) {
          this.selectedMailId.set(mails[0]?.id ?? null);
        }
        if (this.selectedMailId() === null && mails.length) {
          this.selectedMailId.set(mails[0].id);
        }
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error));
        this.loading.set(false);
      }
    });
  }

  clearMails(): void {
    this.devMailsService.clear().subscribe({
      next: () => {
        this.mails.set([]);
        this.selectedMailId.set(null);
        this.infoMessage.set(this.i18nService.t('devInbox.cleared'));
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error));
      }
    });
  }

  selectRow(row: DataListRow): void {
    this.selectedMailId.set(Number(row['id']));
  }

  private formatDate(value: string): string {
    return new Date(value).toLocaleString(this.i18nService.locale(), {
      dateStyle: 'short',
      timeStyle: 'short'
    });
  }

  private toErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (typeof error.error?.message === 'string' && error.error.message.trim().length) {
        return error.error.message;
      }

      if (error.status === 403) {
        return this.i18nService.t('devInbox.errors.forbidden');
      }

      if (error.status === 404) {
        return this.i18nService.t('devInbox.errors.disabled');
      }

      if (error.status === 0) {
        return this.i18nService.t('devInbox.errors.backend');
      }
    }

    return this.i18nService.t('devInbox.errors.load');
  }
}
