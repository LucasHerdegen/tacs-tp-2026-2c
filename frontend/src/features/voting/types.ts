import type { Actividad } from '../activities/types';
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
  usuarioId: number;
  numeroAlternativa: number;
}

export interface AlternativaDto {
  id: number;
  fecha: string;
  clima: Clima | null;
  numeroAlternativa: number;
  cantidadVotos: number;
  // null = la actividad no tiene ReglasClima definidas (no aplica)
  cumpleReglasClima: boolean | null;
}

export interface VotacionDto {
  id: number;
  fechaApertura: string;
  fechaLimite: string;
  fechaCierre: string | null;
  actividadDto: Actividad;
  alternativasDtos: AlternativaDto[];
  quorumMinimo: number;
  abierta: boolean;
  alternativaGanadora: AlternativaDto | null;
}