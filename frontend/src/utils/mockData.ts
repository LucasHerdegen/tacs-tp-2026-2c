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

export const mockActivities: Activity[] = [
  {
    id: '1',
    title: 'Asado de Fin de Año',
    description: 'Juntada para despedir el año con los de la facu. Traigan algo para tomar.',
    type: 'OUTDOOR',
    location: 'Parque Sarmiento, CABA',
    date: '2026-11-14T12:30:00',
    minParticipants: 5,
    maxParticipants: 20,
    currentParticipants: 12,
    weatherCondition: 'IDEAL',
    estado: 'CONFIRMADA',
    isJoined: false,
    isOrganizer: true,
  },
  {
    id: '2',
    title: 'Partido Fútbol 5',
    description: 'Partido tranquilo, nivel amateur. Faltan un par para completar.',
    type: 'MIXED',
    location: 'Canchas El Templo, Palermo',
    date: '2026-11-17T20:00:00',
    minParticipants: 10,
    maxParticipants: 10,
    currentParticipants: 9,
    weatherCondition: 'WARNING',
    estado: 'REPROGRAMADA',
    isJoined: true,
    isOrganizer: false,
  },
  {
    id: '3',
    title: 'Juegos de Mesa',
    description: 'Nos juntamos a jugar al Catan y al TEG. Hay pizza.',
    type: 'INDOOR',
    location: 'Caballito, CABA',
    date: '2026-11-20T19:00:00',
    minParticipants: 3,
    maxParticipants: 6,
    currentParticipants: 3,
    weatherCondition: 'BAD',
    estado: 'PROPUESTA',
    isJoined: false,
    isOrganizer: true,
  },
  {
    id: '4',
    title: 'Trekking en las sierras',
    description: 'Salida temprano a caminar por la montaña.',
    type: 'OUTDOOR',
    location: 'Sierra de la Ventana',
    date: '2026-12-05T08:00:00',
    minParticipants: 2,
    maxParticipants: 15,
    currentParticipants: 15,
    weatherCondition: 'IDEAL',
    estado: 'FINALIZADA',
    isJoined: true,
    isOrganizer: false,
  },
  {
    id: '5',
    title: 'Cena de Egresados',
    description: 'Cena de despedida para los que terminan la carrera este cuatrimestre.',
    type: 'INDOOR',
    location: 'Palermo Soho, CABA',
    date: '2026-10-30T21:00:00',
    minParticipants: 8,
    maxParticipants: 25,
    currentParticipants: 6,
    weatherCondition: 'BAD',
    estado: 'CANCELADA',
    isJoined: true,
    isOrganizer: false,
  }
];
