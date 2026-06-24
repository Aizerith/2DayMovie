export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface Task {
  id: number;
  projectId: number;
  projectName: string;
  ownerId: number;
  ownerName: string;
  assigneeId: number | null;
  assigneeName: string | null;
  assigneeEmail: string | null;
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  manageable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTaskRequest {
  projectId: number;
  assigneeId?: number | null;
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate?: string | null;
}

export interface UpdateTaskRequest {
  projectId: number;
  assigneeId?: number | null;
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate?: string | null;
}
