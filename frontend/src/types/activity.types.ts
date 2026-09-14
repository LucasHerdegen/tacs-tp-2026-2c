export const TipoActividad = {
  AIRE_LIBRE: 'AIRE_LIBRE',
  TECHADA: 'TECHADA',
  MIXTA: 'MIXTA'
} as const;

export type TipoActividad = typeof TipoActividad[keyof typeof TipoActividad];

export interface Ubicacion {
  barrio: string;
  latitud: number;
  longitud: number;
}

export interface ActividadPostDto {
  titulo: string;
  descripcion?: string;
  tipoActividad: TipoActividad;
  ubicacion: Ubicacion;
  fecha: string;
  duracionEstimada: number;
  cantidadMinima: number;
  cantidadMaxima: number;
}

export interface ConfigurarCondicionesDto {
  horasAnticipacion: number;
  rangoReprogramacion: RangoReprogramacionDto;
  reglasClima: ReglasClimaDto;
}

export interface RangoReprogramacionDto {
  dias: number;
  horaInicio: number;
  horaFinal: number;
}

export interface ReglasClimaDto {
  maxProbabilidadLluvia: number | null;
  minTemperatura: number | null;
  maxTemperatura: number | null;
  maxViento: number | null;
}

export interface ActividadDto {
  id: number;
  titulo: string;
  descripcion?: string;
  tipoActividad: TipoActividad;
  ubicacion: Ubicacion;
  fecha: string;
  duracionEstimada: number;
  cantidadMinima: number;
  cantidadMaxima: number;
  estado: string;
}