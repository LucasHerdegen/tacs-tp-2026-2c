package com.tacs.backend.repositories;

import com.tacs.backend.domain.votacion.Votacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VotacionesRepository
{
  List<Votacion> findByAbiertaAndActividadOrganizadorId(boolean abierta, Long organizadorId);

  List<Votacion> findByAbiertaAndActividadParticipantesId(boolean abierta, Long usuarioId);

  Optional<Votacion> findByAbiertaTrueAndActividadId(Long actividadId);

  List<Votacion> findByAbiertaTrueAndFechaLimiteBefore(LocalDateTime ahora);

  List<Votacion> findByAbiertaYUsuarioInvolucrado(boolean abierta, Long usuarioId);

  Votacion save(Votacion votacion);

  Optional<Votacion> findById(Long id);

  void delete(Votacion votacion);
}
