import type { Actividad } from './types';
import type { User } from '../auth/types';

export const canManageActivity = (actividad: Actividad | null, user: User | null): boolean => {
  if (!actividad || !user) return false;
  
  return actividad.organizador.id === user.id;
};

export const isParticipant = (actividad: Actividad | null, user: User | null): boolean => {
  if (!actividad || !user) return false;
  return actividad.participantes.some((p) => p.id === user.id);
};

export function isActivityCanceled(activity: Actividad | null): boolean {
  return activity?.estadoActividad === 'CANCELADA';
}