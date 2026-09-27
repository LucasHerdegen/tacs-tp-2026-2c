import { apiRequest } from '../../lib/api';

import type { Actividad, ActividadPost, PronosticoRespuesta, ConfigurarCondiciones } from './types';

export const activitiesApi = {
  async obtener(id: string, token: string): Promise<Actividad | null> {
    try {
      return await apiRequest<Actividad>(`/api/actividades/${id}`, { token });
    } catch (error) {
      return null;
    }
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

  unirse(actividadId: string, token: string) {
    return apiRequest<void>(
      `/api/actividades/${actividadId}/participantes`,
      { method: 'POST', token },
    );
  },

  bajarse(actividadId: string, token: string) {
    return apiRequest<void>(
      `/api/actividades/${actividadId}/participantes`,
      { method: 'DELETE', token },
    );
  },

  clima(actividadId: string, token: string) {
    return apiRequest<PronosticoRespuesta>(
      `/api/actividades/${actividadId}/clima`,
      { token },
    );
  },

  cancelar(actividadId: string, token: string) {
    return apiRequest<void>(
      `/api/actividades/${actividadId}/estado`,
      { method: 'PATCH', token, body: JSON.stringify({ estado: 'CANCELADA' }) },
    );
  },

  configurarClima(
    actividadId: string,
    configuracion: ConfigurarCondiciones,
    token: string,
  ) {
    return apiRequest<void>(
      `/api/actividades/${actividadId}/configuracion-clima`,
      { method: 'PATCH', token, body: JSON.stringify(configuracion) },
    );
  },

  buscar(
    tipo: string | null,
    busqueda: string | null,
    fecha: string | null,
    estado: string | null,
    page: number = 0,
    size: number = 20,
    token: string
  ) {
    const params = new URLSearchParams();

    if (tipo) {
      params.append('tipo', tipo);
    }
    if (busqueda) {
      params.append('busqueda', busqueda);
    }
    if (fecha) {
      params.append('fecha', fecha);
    }
    if (estado) {
      params.append('estado', estado);
    }
    
    params.append('page', page.toString());
    params.append('size', size.toString());

    const query = params.toString();

    return apiRequest<{ content: Actividad[]; totalPages: number; number: number; last: boolean; first: boolean }>(
      `/api/actividades${query ? `?${query}` : ''}`,
      { token },
    );
  },
};
