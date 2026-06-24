import {Component, computed, inject, input, output, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TranslocoPipe} from '@jsverse/transloco';
import {I18nService} from '../../../core/i18n/i18n.service';

export interface DataListColumn {
  key: string;
  label: string;
  sortable?: boolean;
  sortKey?: string;
}

export type DataListRow = Record<string, string>;
export type DataListSortDirection = 'asc' | 'desc';
export interface DataListSortChange {
  key: string;
  direction: DataListSortDirection;
}

@Component({
  selector: 'app-data-list',
  imports: [FormsModule, TranslocoPipe],
  templateUrl: './data-list.html',
})
export class DataList {
  private readonly i18nService = inject(I18nService);
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly columns = input<DataListColumn[]>([]);
  readonly rows = input<DataListRow[]>([]);
  readonly keyField = input('id');
  readonly selectable = input(false);
  readonly selectedKey = input('');
  readonly searchable = input(true);
  readonly searchPlaceholder = input('');
  readonly sortable = input(true);
  readonly defaultSortKey = input('');
  readonly initialPageSize = input(5);
  readonly pageSizeOptions = input<number[]>([5, 10, 20]);
  readonly serverSide = input(false);
  readonly totalItems = input(0);
  readonly currentPageIndex = input(0);

  readonly rowSelected = output<DataListRow>();
  readonly pageChanged = output<number>();
  readonly pageSizeChanged = output<number>();
  readonly sortChanged = output<DataListSortChange>();

  readonly searchQuery = signal('');
  readonly sortKey = signal('');
  readonly sortDirection = signal<'asc' | 'desc'>('asc');
  readonly currentPage = signal(1);
  readonly pageSize = signal(5);

  readonly filteredRows = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const visibleRows = !query
      ? this.rows()
      : this.rows().filter(row =>
        this.columns().some(column => (row[column.key] ?? '').toLowerCase().includes(query))
      );

    const activeSortKey = this.currentSortKey();
    if (!activeSortKey) {
      return visibleRows;
    }

    return [...visibleRows].sort((left, right) => {
      const leftValue = (left[activeSortKey] ?? '').toLowerCase();
      const rightValue = (right[activeSortKey] ?? '').toLowerCase();
      const comparison = leftValue.localeCompare(rightValue, this.i18nService.locale(), {numeric: true, sensitivity: 'base'});

      return this.sortDirection() === 'asc' ? comparison : -comparison;
    });
  });

  readonly totalPages = computed(() => {
    if (this.serverSide()) {
      const total = Math.ceil(this.totalItems() / this.pageSize());
      return Math.max(total, 1);
    }

    const total = Math.ceil(this.filteredRows().length / this.pageSize());
    return Math.max(total, 1);
  });
  readonly displayedPage = computed(() => this.serverSide()
    ? Math.min(this.currentPageIndex() + 1, this.totalPages())
    : Math.min(this.currentPage(), this.totalPages())
  );

  readonly paginatedRows = computed(() => {
    if (this.serverSide()) {
      return this.rows();
    }

    const boundedPage = this.displayedPage();
    const start = (boundedPage - 1) * this.pageSize();
    return this.filteredRows().slice(start, start + this.pageSize());
  });

  readonly displayedTotal = computed(() => this.serverSide() ? this.totalItems() : this.filteredRows().length);
  readonly startItem = computed(() => this.displayedTotal() ? (this.displayedPage() - 1) * this.pageSize() + 1 : 0);
  readonly endItem = computed(() => Math.min(this.startItem() + this.paginatedRows().length - 1, this.displayedTotal()));

  constructor() {
    this.sortKey.set(this.defaultSortKey());
    this.pageSize.set(this.initialPageSize());
  }

  updateSearchQuery(query: string): void {
    this.searchQuery.set(query);
    this.currentPage.set(1);
  }

  updatePageSize(value: string): void {
    const parsedValue = Number(value);
    const nextPageSize = parsedValue > 0 ? parsedValue : this.initialPageSize();
    this.pageSize.set(nextPageSize);
    this.currentPage.set(1);

    if (this.serverSide()) {
      this.pageSizeChanged.emit(nextPageSize);
    }
  }

  toggleSort(column: DataListColumn): void {
    if (!this.sortable() || column.sortable === false) {
      return;
    }

    if (this.currentSortKey() === column.key) {
      const nextDirection = this.sortDirection() === 'asc' ? 'desc' : 'asc';
      this.sortDirection.set(nextDirection);
      this.emitSortChange(column.sortKey ?? column.key, nextDirection);
      return;
    }

    this.sortKey.set(column.key);
    this.sortDirection.set('asc');
    this.emitSortChange(column.sortKey ?? column.key, 'asc');
  }

  previousPage(): void {
    if (this.serverSide()) {
      this.pageChanged.emit(Math.max(this.currentPageIndex() - 1, 0));
      return;
    }

    this.currentPage.set(Math.max(this.currentPage() - 1, 1));
  }

  nextPage(): void {
    if (this.serverSide()) {
      this.pageChanged.emit(Math.min(this.currentPageIndex() + 1, this.totalPages() - 1));
      return;
    }

    this.currentPage.set(Math.min(this.currentPage() + 1, this.totalPages()));
  }

  rowTrackKey(row: DataListRow, index: number): string {
    const key = row[this.keyField()];
    return key?.trim() ? key : `row-${this.displayedPage()}-${index}`;
  }

  sortIndicator(column: DataListColumn): string {
    if (this.currentSortKey() !== column.key) {
      return '↕';
    }

    return this.sortDirection() === 'asc' ? '↑' : '↓';
  }

  isColumnSortable(column: DataListColumn): boolean {
    return this.sortable() && column.sortable !== false;
  }

  private currentSortKey(): string {
    if (this.sortKey()) {
      return this.sortKey();
    }

    if (this.defaultSortKey()) {
      return this.defaultSortKey();
    }

    const firstSortableColumn = this.columns().find(column => column.sortable !== false);
    return firstSortableColumn?.key ?? '';
  }

  private emitSortChange(key: string, direction: DataListSortDirection): void {
    if (this.serverSide()) {
      this.sortChanged.emit({key, direction});
    }
  }
}
