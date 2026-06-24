export interface RealtimeNotification {
  type: string;
  message: string;
  payload: Record<string, unknown>;
  createdAt: string;
}
