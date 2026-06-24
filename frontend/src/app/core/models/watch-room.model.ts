export interface UploadAssetRequest {
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
}

export interface CreateWatchRoomRequest {
  title: string;
  pin: string;
  video: UploadAssetRequest;
  subtitle: UploadAssetRequest | null;
}

export interface PresignedUpload {
  url: string;
  objectKey: string;
}

export interface CreateWatchRoomResponse {
  shareCode: string;
  shareUrl: string;
  videoUpload: PresignedUpload;
  subtitleUpload: PresignedUpload | null;
  expiresAt: string;
}

export interface WatchRoomAccessResponse {
  shareCode: string;
  title: string;
  videoUrl: string;
  subtitleUrl: string | null;
  videoContentType: string;
  playbackTimeSeconds: number;
  playing: boolean;
}

export interface PlaybackSyncMessage {
  pin: string;
  clientId: string;
  currentTime: number;
  playing: boolean;
  event: string;
  sentAt: number;
}
