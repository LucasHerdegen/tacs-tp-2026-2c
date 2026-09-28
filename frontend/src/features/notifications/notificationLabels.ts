import type { TipoNotificacion } from './types';

export const TIPO_NOTIFICACION_ICON: Record<TipoNotificacion, string> = {
  CLIMA_DESFAVORABLE: '⛈️',
  RECORDATORIO_INICIO: '⏰',
  ACTIVIDAD_CANCELADA: '🚫',
  REPROGRAMACION: '🔄',
};