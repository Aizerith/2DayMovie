import {Component, computed, inject, signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {catchError, of, switchMap, throwError} from 'rxjs';
import {TranslocoPipe} from '@jsverse/transloco';
import {AuthService} from '../../../../core/services/auth.service';
import {FilesService} from '../../../../core/services/files.service';
import {StoredFile, StoredFileStatus} from '../../../../core/models/file.model';
import {I18nService} from '../../../../core/i18n/i18n.service';
import {DataList, DataListColumn, DataListRow, DataListSortChange} from '../../../../shared/ui/data-list/data-list';
import {EmptyState} from '../../../../shared/ui/empty-state/empty-state';
import {ModalConfirm} from '../../../../shared/ui/modal-confirm/modal-confirm';
import {NoticeBanner} from '../../../../shared/ui/notice-banner/notice-banner';
import {PageIntro} from '../../../../shared/ui/page-intro/page-intro';
import {SectionCard} from '../../../../shared/ui/section-card/section-card';
import {Toolbar} from '../../../../shared/ui/toolbar/toolbar';

@Component({
  selector: 'app-files',
  imports: [
    TranslocoPipe,
    DataList,
    EmptyState,
    ModalConfirm,
    NoticeBanner,
    PageIntro,
    SectionCard,
    Toolbar
  ],
  templateUrl: './files.html',
})
export class Files {
  private readonly filesService = inject(FilesService);
  private readonly authService = inject(AuthService);
  private readonly i18nService = inject(I18nService);

  readonly files = signal<StoredFile[]>([]);
  readonly selectedFileId = signal<number | null>(null);
  readonly selectedLocalFile = signal<File | null>(null);
  readonly loading = signal(false);
  readonly uploading = signal(false);
  readonly working = signal(false);
  readonly deleteModalOpen = signal(false);
  readonly errorMessage = signal('');
  readonly infoMessage = signal('');
  readonly page = signal(0);
  readonly pageSize = signal(5);
  readonly totalItems = signal(0);
  readonly sort = signal('updatedAt,desc');
  readonly canViewAll = computed(() => {
    const role = this.authService.getCurrentUserRole();
    return role === 'ADMIN' || role === 'MANAGER';
  });

  readonly columns = computed<DataListColumn[]>(() => {
    this.i18nService.activeLang();

    return [
      {key: 'name', label: this.i18nService.t('files.columns.name'), sortKey: 'originalFilename'},
      {key: 'status', label: this.i18nService.t('files.columns.status')},
      {key: 'size', label: this.i18nService.t('files.columns.size'), sortKey: 'sizeBytes'},
      {key: 'owner', label: this.i18nService.t('files.columns.owner'), sortable: false},
      {key: 'updatedAt', label: this.i18nService.t('files.columns.updatedAt')}
    ];
  });

  constructor() {
    this.loadFiles();
  }

  get selectedStoredFile(): StoredFile | null {
    return this.files().find(file => file.id === this.selectedFileId()) ?? null;
  }

  get selectedRowKey(): string {
    return this.selectedFileId() !== null ? String(this.selectedFileId()) : '';
  }

  get rows(): DataListRow[] {
    this.i18nService.activeLang();

    return this.files().map(file => ({
      id: String(file.id),
      name: file.originalFilename,
      status: this.statusLabel(file.status),
      size: this.formatSize(file.sizeBytes),
      owner: file.ownerName,
      updatedAt: this.formatDate(file.updatedAt)
    }));
  }

  loadFiles(): void {
    this.loading.set(true);
    this.filesService.findPage({page: this.page(), size: this.pageSize(), sort: this.sort()}).subscribe({
      next: (response) => {
        this.files.set(response.items);
        this.totalItems.set(response.totalItems);
        this.errorMessage.set('');
        if (this.selectedFileId() && !response.items.some(file => file.id === this.selectedFileId())) {
          this.selectedFileId.set(null);
        }
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('files.errors.load')));
        this.loading.set(false);
      }
    });
  }

  updatePage(page: number): void {
    this.page.set(page);
    this.loadFiles();
  }

  updatePageSize(size: number): void {
    this.pageSize.set(size);
    this.page.set(0);
    this.loadFiles();
  }

  updateSort(change: DataListSortChange): void {
    this.sort.set(`${change.key},${change.direction}`);
    this.page.set(0);
    this.loadFiles();
  }

  selectRow(row: DataListRow): void {
    this.selectedFileId.set(Number(row['id']));
    this.errorMessage.set('');
    this.infoMessage.set('');
  }

  onFilePicked(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.item(0) ?? null;

    this.selectedLocalFile.set(file);
    this.errorMessage.set('');
    this.infoMessage.set(file ? this.i18nService.t('files.selectionReady') : '');
  }

  clearLocalSelection(): void {
    this.selectedLocalFile.set(null);
    this.infoMessage.set('');
  }

  uploadSelected(): void {
    const localFile = this.selectedLocalFile();

    if (!localFile) {
      this.errorMessage.set(this.i18nService.t('files.errors.selectLocal'));
      return;
    }

    this.uploading.set(true);
    this.errorMessage.set('');
    this.infoMessage.set('');

    const contentType = localFile.type || 'application/octet-stream';

    this.filesService.requestUpload({
      originalFilename: localFile.name,
      contentType,
      sizeBytes: localFile.size
    }).pipe(
      switchMap((uploadPlan) =>
        this.filesService.uploadToPresignedUrl(uploadPlan.uploadUrl, localFile, contentType).pipe(
          switchMap(() => this.filesService.completeUpload(uploadPlan.fileId)),
          catchError((error) => this.filesService.delete(uploadPlan.fileId).pipe(
            catchError(() => of(void 0)),
            switchMap(() => throwError(() => error))
          ))
        )
      )
    ).subscribe({
      next: (storedFile) => {
        this.files.set([
          storedFile,
          ...this.files().filter(file => file.id !== storedFile.id)
        ]);
        this.selectedFileId.set(storedFile.id);
        this.selectedLocalFile.set(null);
        this.infoMessage.set(this.i18nService.t('files.uploaded'));
        this.uploading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('files.errors.upload')));
        this.uploading.set(false);
      }
    });
  }

  downloadSelected(): void {
    const selectedFile = this.selectedStoredFile;

    if (!selectedFile) {
      return;
    }

    this.working.set(true);
    this.errorMessage.set('');
    this.infoMessage.set('');

    this.filesService.createDownloadUrl(selectedFile.id).subscribe({
      next: (response) => {
        const link = document.createElement('a');
        link.href = response.downloadUrl;
        link.download = response.fileName;
        link.target = '_blank';
        link.rel = 'noopener';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        this.infoMessage.set(this.i18nService.t('files.downloadReady'));
        this.working.set(false);
      },
      error: (error) => {
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('files.errors.download')));
        this.working.set(false);
      }
    });
  }

  askDelete(): void {
    if (!this.selectedStoredFile) {
      return;
    }

    this.deleteModalOpen.set(true);
  }

  confirmDelete(): void {
    const selectedFile = this.selectedStoredFile;

    if (!selectedFile) {
      this.deleteModalOpen.set(false);
      return;
    }

    this.working.set(true);

    this.filesService.delete(selectedFile.id).subscribe({
      next: () => {
        this.files.set(this.files().filter(file => file.id !== selectedFile.id));
        this.selectedFileId.set(null);
        this.deleteModalOpen.set(false);
        this.infoMessage.set(this.i18nService.t('files.deleted'));
        this.working.set(false);
      },
      error: (error) => {
        this.deleteModalOpen.set(false);
        this.errorMessage.set(this.toErrorMessage(error, this.i18nService.t('files.errors.delete')));
        this.working.set(false);
      }
    });
  }

  cancelDelete(): void {
    this.deleteModalOpen.set(false);
  }

  statusLabel(status: StoredFileStatus): string {
    switch (status) {
      case 'READY':
        return this.i18nService.t('enums.fileStatus.READY');
      default:
        return this.i18nService.t('enums.fileStatus.PENDING');
    }
  }

  formatSize(sizeBytes: number | null): string {
    if (sizeBytes === null) {
      return this.i18nService.t('files.pendingSize');
    }

    if (sizeBytes < 1024) {
      return `${sizeBytes} B`;
    }

    const kib = sizeBytes / 1024;
    if (kib < 1024) {
      return `${kib.toFixed(1)} KB`;
    }

    return `${(kib / 1024).toFixed(1)} MB`;
  }

  formatDate(value: string | null): string {
    if (!value) {
      return this.i18nService.t('common.notDefined');
    }

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
        return this.i18nService.t('files.errors.session');
      }

      if (error.status === 403) {
        return this.i18nService.t('files.errors.forbidden');
      }

      if (error.status === 0) {
        return this.i18nService.t('files.errors.storage');
      }
    }

    if (error instanceof Error && error.message.trim().length) {
      return error.message;
    }

    return fallbackMessage;
  }
}
