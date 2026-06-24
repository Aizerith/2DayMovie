export interface UserAdmin {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  enabled: boolean;
  emailVerified: boolean;
}

export interface AssignableUser {
  id: number;
  email: string;
  fullName: string;
  role: string;
}

export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
  role: string;
  enabled: boolean;
}

export interface UpdateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  password?: string;
  role: string;
  enabled: boolean;
}
