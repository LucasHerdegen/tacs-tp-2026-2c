import { apiRequest } from '../../lib/api';
import type {
  AlternativaPostDto,
  VotacionDto,
  VotacionPostDto,
  VotoPostDto,
} from './types';

export const votingApi = {
  listar(abierta: boolean, token: string) {
    return apiRequest<VotacionDto[]>(`/api/votaciones?abierta=${abierta}`, { token });
  },

  crear(actividadId: number, dto: VotacionPostDto, token: string) {
    return apiRequest<VotacionDto>(`/api/votaciones?actividadId=${actividadId}`, {
      method: 'POST',
      token,
      body: JSON.stringify(dto),
    });
  },

  obtener(votacionId: number, token: string) {
    return apiRequest<VotacionDto>(`/api/votaciones/${votacionId}`, { token });
  },

  agregarAlternativa(votacionId: number, dto: AlternativaPostDto, token: string) {
    return apiRequest<VotacionDto>(`/api/votaciones/${votacionId}/alternativas`, {
      method: 'POST',
      token,
      body: JSON.stringify(dto),
    });
  },

  eliminarAlternativa(votacionId: number, numeroAlternativa: number, token: string) {
    return apiRequest<void>(
      `/api/votaciones/${votacionId}/alternativas/${numeroAlternativa}`,
      { method: 'DELETE', token },
    );
  },

  votar(votacionId: number, dto: VotoPostDto, token: string) {
    return apiRequest<VotacionDto>(`/api/votaciones/${votacionId}/votos`, {
      method: 'POST',
      token,
      body: JSON.stringify(dto),
    });
  },

  cerrar(votacionId: number, token: string) {
    return apiRequest<VotacionDto>(`/api/votaciones/${votacionId}/cierre`, {
      method: 'POST',
      token,
    });
  },

  eliminar(votacionId: number, token: string) {
    return apiRequest<void>(`/api/votaciones/${votacionId}`, {
      method: 'DELETE',
      token,
    });
  },

  async buscarPorActividad(actividadId: number, token: string): Promise<VotacionDto | null> {
    const [abiertas, cerradas] = await Promise.all([
      votingApi.listar(true, token),
      votingApi.listar(false, token),
    ]);

    const candidatas = [...abiertas, ...cerradas]
      .filter((v) => v.actividadDto.id === actividadId)
      .sort((a, b) => b.fechaApertura.localeCompare(a.fechaApertura));

    return candidatas[0] ?? null;
  },
};