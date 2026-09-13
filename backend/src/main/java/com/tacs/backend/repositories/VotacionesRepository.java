package com.tacs.backend.repositories;

import com.tacs.backend.domain.votacion.Votacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VotacionesRepository
{
  List<Votacion> findByAbiertaAndActividadOrganizadorId(boolean abierta, String organizadorId);

  List<Votacion> findByAbiertaAndActividadParticipantesId(boolean abierta, String usuarioId);

  Optional<Votacion> findByAbiertaTrueAndActividadId(String actividadId);

  List<Votacion> findByAbiertaTrueAndFechaLimiteBefore(LocalDateTime ahora);

  List<Votacion> findByAbiertaYUsuarioInvolucrado(boolean abierta, String usuarioId);

  Votacion save(Votacion votacion);

  Optional<Votacion> findById(String id);

  void delete(Votacion votacion);
}
