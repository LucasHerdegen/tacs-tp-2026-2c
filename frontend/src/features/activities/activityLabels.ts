import type { BadgeVariant } from '../../components/ui/Badge';
import type { TipoEstadoActividad } from './types';

export const ESTADO_BADGE: Record<TipoEstadoActividad, { variant: BadgeVariant; label: string }> = {
  PROPUESTA: { variant: 'info', label: 'Propuesta' },
  CONFIRMADA: { variant: 'success', label: 'Confirmada' },
  REPROGRAMADA: { variant: 'warning', label: 'Reprogramada' },
  CANCELADA: { variant: 'error', label: 'Cancelada' },
  FINALIZADA: { variant: 'neutral', label: 'Finalizada' },
};
