package com.tacs.backend.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;

import java.util.List;
import java.util.Optional;

public interface ActividadesRepository
{
  List<Actividad> findByOrganizadorId(Long organizadorId);

  List<Actividad> findByOrganizadorIdAndEstado(Long organizadorId, TipoEstadoActividad estado);

  List<Actividad> findByParticipantesId(Long usuarioId);

  List<Actividad> findByParticipantesIdAndEstado(Long usuarioId, TipoEstadoActividad estado);

  List<Actividad> findByOrganizadorIdOrParticipantesId(Long usuarioId);

  List<Actividad> findByOrganizadorIdOrParticipantesIdAndEstado(Long usuarioId, TipoEstadoActividad estado);

  long countByEstado(TipoEstadoActividad estado);

  List<Actividad> findCandidatasParaChequeoClima();

  List<Actividad> findByEstadoAndRecordatorioEnviadoFalse(TipoEstadoActividad estado);

  List<Actividad> findCandidatasParaRecordatorio();

  Actividad save(Actividad actividad);

  Optional<Actividad> findById(Long id);

  List<Actividad> findAll();

  long count();
}