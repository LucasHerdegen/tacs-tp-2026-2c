export type EstadoActividad = 'PROPUESTA' | 'CONFIRMADA' | 'REPROGRAMADA' | 'CANCELADA' | 'FINALIZADA';

export interface Activity {
  id: string;
  title: string;
  description: string;
  type: 'OUTDOOR' | 'INDOOR' | 'MIXED';
  location: string;
  date: string;
  minParticipants: number;
  maxParticipants: number;
  currentParticipants: number;
  weatherCondition: 'IDEAL' | 'WARNING' | 'BAD';
  estado: EstadoActividad;
  isJoined: boolean; // Simula si el usuario actual ya está anotado
  isOrganizer: boolean;
}