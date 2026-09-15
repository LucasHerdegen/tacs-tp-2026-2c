import { apiRequest } from '../../lib/api';
import type { User, UserId, UserRole } from '../auth/types';
import type { EstadisticasDto } from './types';

export const adminApi = {
  estadisticas(token: string) {
    return apiRequest<EstadisticasDto>('/api/admin/estadisticas', { token });
  },

  usuarios(token: string) {
    return apiRequest<User[]>('/api/usuarios', { token });
  },

  actualizarRol(usuarioId: UserId, rol: UserRole, token: string) {
    return apiRequest<User>(`/api/usuarios/${usuarioId}/rol`, {
      method: 'PATCH',
      token,
      body: JSON.stringify({ rol }),
    });
  },
};
