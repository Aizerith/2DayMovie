export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

export interface Project {
  id: number;
  name: string;
  description: string;
  status: ProjectStatus;
  ownerId: number;
  ownerName: string;
  ownerEmail: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectRequest {
  name: string;
  description: string;
  status: ProjectStatus;
}

export interface UpdateProjectRequest {
  name: string;
  description: string;
  status: ProjectStatus;
}
