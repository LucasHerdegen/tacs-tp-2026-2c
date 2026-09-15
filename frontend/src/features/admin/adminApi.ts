import { apiRequest } from '../../lib/api';
import type { EstadisticasDto } from './types';

export const adminApi = {
  estadisticas(token: string) {
    return apiRequest<EstadisticasDto>('/api/admin/estadisticas', { token });
  },
};
