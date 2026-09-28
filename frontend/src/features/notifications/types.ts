export type TipoNotificacion =
  | 'CLIMA_DESFAVORABLE'
  | 'RECORDATORIO_INICIO'
  | 'ACTIVIDAD_CANCELADA'
  | 'REPROGRAMACION';

export interface NotificacionDto {
  id: string;
  contenido: string;
  tipo: TipoNotificacion;
  actividadId: string | null;
  votacionId: string | null;
  leida: boolean;
  fechaCreacion: string;
  fechaLectura: string | null;
}

export interface ContadorNoLeidasDto {
  cantidad: number;
}

// Forma que devuelve Spring Data Page<T> serializado a JSON.
// Solo tipamos los campos que efectivamente usamos.
export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}