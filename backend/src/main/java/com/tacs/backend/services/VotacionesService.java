package com.tacs.backend.services;

import com.tacs.backend.dtos.votacion.AlternativaPostDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.dtos.votacion.VotacionPostDto;

import java.util.List;
import java.util.Optional;

public interface VotacionesService
{
  List<VotacionDto> votaciones(String usuarioId, boolean abierta);

  VotacionDto crearVotacion(String actividadId, VotacionPostDto votacionPostDto, String usuarioId);

  /**
   * Busca fechas con clima favorable para la actividad en los proximos dias
   * y, si encuentra alguna, abre una votacion automatica entre esas alternativas
   * (quorumMinimo = minimoParticipantes de la actividad). Si no encuentra ninguna
   * fecha favorable, cancela la actividad directamente en vez de abrir una
   * votacion sin alternativas viables, y devuelve Optional.empty().
   * Lanza IllegalStateException (igual que crearVotacion) si la actividad
   * ya tiene una votacion abierta.
   */
  Optional<VotacionDto> abrirVotacionAutomatica(String actividadId);

  VotacionDto obtenerVotacion(String votacionId);

  VotacionDto agregarAlternativa(String votacionId, AlternativaPostDto alternativaPostDto, String usuarioId);

  void eliminarAlternativa(String votacionId, int numeroAlternativa, String usuarioId);

  /**
   * Registra (o actualiza, si ya habia votado antes) el voto de un participante.
   * Solo pueden votar quienes participan de la actividad asociada.
   */
  VotacionDto votar(String votacionId, String usuarioId, int numeroAlternativa);

  /**
   * Numero de la alternativa que el usuario ya voto en esa votacion, o vacio si todavia no voto.
   */
  Optional<Integer> alternativaVotadaPor(String votacionId, String usuarioId);

  /**
   * Cierra la votacion y resuelve la actividad asociada: si la alternativa
   * mas votada alcanza el quorumMinimo, reprograma la actividad a esa fecha;
   * si no, la cancela. Puede dispararse manualmente o desde el cron de
   * cierre automatico por fechaLimite vencida.
   */
  VotacionDto resolverVotacion(String votacionId, String usuarioId);
  
  VotacionDto resolverVotacion(String votacionId); // Para ser llamado desde los jobs o internamente

  void eliminarVotacion(String votacionId, String usuarioId);
}
