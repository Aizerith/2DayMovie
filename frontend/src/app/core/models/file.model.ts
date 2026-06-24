export type StoredFileStatus = 'PENDING' | 'READY';

export interface StoredFile {
  id: number;
  originalFilename: string;
  contentType: string;
  sizeBytes: number | null;
  status: StoredFileStatus;
  ownerId: number;
  ownerName: string;
  ownerEmail: string;
  uploadedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InitiateFileUploadRequest {
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
}

export interface InitiateFileUploadResponse {
  fileId: number;
  uploadUrl: string;
  expiresAt: string;
}

export interface PresignedDownloadResponse {
  fileName: string;
  downloadUrl: string;
  expiresAt: string;
}
