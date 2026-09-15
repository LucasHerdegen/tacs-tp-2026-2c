import { apiRequest } from '../../lib/api';
import type {
  LoginCredentials,
  LoginResponse,
  RegisterData,
  User,
} from './types';

export const authApi = {
  login(credentials: LoginCredentials) {
    return apiRequest<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
    });
  },

  register(data: RegisterData) {
    return apiRequest<User>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  me(token: string) {
    return apiRequest<User>('/api/auth/me', { token });
  },
};
