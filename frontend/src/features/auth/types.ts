export type UserRole = 'USER' | 'ADMIN';
export type UserId = string | number;

export interface ContactMethod {
  valor: string;
  tipo: 'TELEGRAM';
}

export interface User {
  id: UserId;
  username: string;
  rol: UserRole;
  medioContacto: ContactMethod | null;
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
