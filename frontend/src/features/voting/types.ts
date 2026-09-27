import type { Clima } from '../activities/types';


export interface AlternativaPostDto {
  fecha: string;
}

export interface VotacionPostDto {
  quorumMinimo: number;
  fechaLimite: string;
  alternativas: AlternativaPostDto[];
}

export interface VotoPostDto {
  numeroAlternativa: number;
}

export interface AlternativaDto {
  id: string;
  fecha: string;
  clima: Clima | null;
  numeroAlternativa: number;
  cantidadVotos: number;
  // null = la actividad no tiene ReglasClima definidas (no aplica)
  cumpleReglasClima: boolean | null;
}

export interface VotacionDto {
  id: string;
  fechaApertura: string;
  fechaLimite: string;
  fechaCierre: string | null;
  actividad: {
    id: string;
    titulo: string;
    estado: string;
    fechaRealizacion: string;
  };
  alternativasDtos: AlternativaDto[];
  quorumMinimo: number;
  abierta: boolean;
  alternativaGanadora: AlternativaDto | null;
}
