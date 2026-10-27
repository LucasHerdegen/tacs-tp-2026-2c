import { apiRequest } from '../../lib/api';
import type {
  LoginCredentials,
  LoginResponse,
  RegisterData,
  TipoProveedorClima,
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
    return apiRequest<User>('/api/usuarios/me', { token });
  },

  actualizarProveedorClima(proveedorClima: TipoProveedorClima, token: string) {
    return apiRequest<User>('/api/usuarios/me/proveedor-clima', {
      method: 'PATCH',
      token,
      body: JSON.stringify({ proveedorClima }),
    });
  },
};
