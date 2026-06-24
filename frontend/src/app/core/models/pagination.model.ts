export interface PageRequest {
  page: number;
  size: number;
  sort?: string;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}
