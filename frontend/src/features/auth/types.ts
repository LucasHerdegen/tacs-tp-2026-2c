export type UserRole = 'USER' | 'ADMIN';

export interface User {
  id: number;
  username: string;
  rol: UserRole;
}

export interface LoginCredentials {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

export type RegisterData = LoginCredentials;

export interface StoredSession {
  token: string;
  expiresAt: number;
}
