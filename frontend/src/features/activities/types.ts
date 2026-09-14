import type { User } from '../auth/types';

export type TipoActividad = 'AIRE_LIBRE' | 'TECHADA' | 'MIXTA';

export type TipoEstadoActividad =
  | 'PROPUESTA'
  | 'CONFIRMADA'
  | 'REPROGRAMADA'
  | 'CANCELADA'
  | 'FINALIZADA';

export interface Ubicacion {
  barrio: string;
  latitud: number;
  longitud: number;
}

export interface RangoReprogramacion {
  dias: number;
  horaInicio: number;
  horaFinal: number;
}

export interface CambioFecha {
  fecha: string;
  fechaAntigua: string;
  fechaNueva: string;
}

export interface ReglasClima {
  maxProbabilidadLluvia: number;
  minTemperatura: number;
  maxTemperatura: number;
  maxViento: number;
}

export interface Clima {
  probabilidadLluvia: number;
  temperatura: number;
  viento: number;
}

export interface PronosticoRespuesta {
  climaActual: Clima;
  pronosticoFuturo: Clima;
}

export interface Actividad {
  id: number;
  titulo: string;
  descripcion: string;
  tipoActividad: TipoActividad;
  ubicacion: Ubicacion;
  fecha: string;
  duracionEstimada: number;
  minimoParticipantes: number;
  maximoParticipantes: number;
  organizador: User;
  participantes: User[];
  horasAnticipacion: number;
  rangoReprogramacion: RangoReprogramacion | null;
  cambiosFecha: CambioFecha[];
  estadoActividad: TipoEstadoActividad;
  reglasClima: ReglasClima | null;
}