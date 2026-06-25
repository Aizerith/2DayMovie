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
  videoUrl: string | null;
  subtitleUrl: string | null;
  subtitleTracks: SubtitleTrackResponse[];
  audioTracks: AudioTrackResponse[];
  videoContentType: string | null;
  status: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';
  playbackTimeSeconds: number;
  playing: boolean;
}

export interface SubtitleTrackResponse {
  label: string;
  language: string;
  url: string;
}

export interface AudioTrackResponse {
  label: string;
  language: string;
  url: string;
}

export interface PlaybackSyncMessage {
  pin: string;
  clientId: string;
  currentTime: number;
  playing: boolean;
  event: string;
  sentAt: number;
}

export interface PresenceParticipant {
  clientId: string;
  displayName: string;
  avatar: string;
  joinedAt: number;
}

export interface PresenceMessage {
  pin: string;
  clientId: string;
  displayName: string;
  avatar: string;
  event: string;
  participants: PresenceParticipant[];
  sentAt: number;
}
