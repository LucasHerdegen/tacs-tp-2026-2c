import { apiRequest } from '../../lib/api';
import type { ContadorNoLeidasDto, NotificacionDto, Page } from './types';

export const notificationsApi = {
  misNotificaciones(
    token: string,
    opciones: { leida?: boolean; page?: number; size?: number } = {},
  ) {
    const { leida, page = 0, size = 10 } = opciones;
    const params = new URLSearchParams();
    if (leida !== undefined) params.append('leida', String(leida));
    params.append('page', String(page));
    params.append('size', String(size));
    params.append('sort', 'fechaCreacion,desc');

    return apiRequest<Page<NotificacionDto>>(`/api/notificaciones/me?${params.toString()}`, { token });
  },

  contarNoLeidas(token: string) {
    return apiRequest<ContadorNoLeidasDto>('/api/notificaciones/me/no-leidas/count', { token });
  },

  marcarComoLeida(id: string, token: string) {
    return apiRequest<void>(`/api/notificaciones/${id}/leida`, { method: 'PATCH', token });
  },

  marcarTodasComoLeidas(token: string) {
    return apiRequest<void>('/api/notificaciones/me/leidas', { method: 'PATCH', token });
  },
};