import { apiRequest } from '../../lib/api';
import type { Actividad, ActividadPost, PronosticoRespuesta, ConfigurarCondiciones } from './types';

export const activitiesApi = {
  async obtener(id: number, token: string): Promise<Actividad | null> {
    const actividades = await apiRequest<Actividad[]>('/api/actividades', { token });
    return actividades.find((a) => a.id === id) ?? null;
  },

  misActividades(organizador: boolean, token: string) {
    return apiRequest<Actividad[]>(`/api/actividades/me?organizador=${organizador}`, { token });
  },

  crear(actividad: ActividadPost, token: string) {
    return apiRequest<void>(
      '/api/actividades',
      { method: 'POST', token, body: JSON.stringify(actividad) }
      ); 
    },

  unirse(actividadId: number, usuarioId: number, token: string) {
    return apiRequest<void>(
      `/api/actividades/${actividadId}/participantes?usuarioId=${usuarioId}`,
      { method: 'POST', token },
    );
  },

  bajarse(actividadId: number, usuarioId: number, token: string) {
    return apiRequest<void>(
      `/api/actividades/${actividadId}/participantes?usuarioId=${usuarioId}`,
      { method: 'DELETE', token },
    );
  },

  clima(actividadId: number, usuarioId: number, token: string) {
    return apiRequest<PronosticoRespuesta>(
      `/api/actividades/${actividadId}/clima?usuarioId=${usuarioId}`,
      { token },
    );
  },

  cancelar(actividadId: number, token: string) {
    return apiRequest<void>(
      `/api/actividades/${actividadId}/cancelaciones`,
      { method: 'POST', token },
    );
  },

  configurarClima(
    actividadId: number,
    configuracion: ConfigurarCondiciones,
    token: string,
  ) {
    return apiRequest<void>(
      `/api/actividades/${actividadId}/configuracion-clima`,
      { method: 'PATCH', token, body: JSON.stringify(configuracion) },
    );
  },

};