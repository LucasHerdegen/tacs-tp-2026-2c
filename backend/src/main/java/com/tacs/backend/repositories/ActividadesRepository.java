package com.tacs.backend.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.tacs.backend.domain.actividad.TipoActividad;

import java.time.LocalDate;

public interface ActividadesRepository
{
  List<Actividad> findByOrganizadorId(String organizadorId);

  List<Actividad> findByOrganizadorIdAndEstado(String organizadorId, TipoEstadoActividad estado);

  List<Actividad> findByParticipantesId(String usuarioId);

  List<Actividad> findByParticipantesIdAndEstado(String usuarioId, TipoEstadoActividad estado);

  List<Actividad> findByOrganizadorIdOrParticipantesId(String usuarioId);

  List<Actividad> findByOrganizadorIdOrParticipantesIdAndEstado(String usuarioId, TipoEstadoActividad estado);

  long countByEstado(TipoEstadoActividad estado);

  List<Actividad> findCandidatasParaChequeoClima();

  List<Actividad> findByEstadoAndRecordatorioEnviadoFalse(TipoEstadoActividad estado);

  List<Actividad> findCandidatasParaRecordatorio();

  Actividad save(Actividad actividad);

  Optional<Actividad> findById(String id);

  List<Actividad> findAll();

  List<Actividad> findCandidatasParaFinalizacion(java.time.LocalDateTime now);

  Page<Actividad> buscarActividades(TipoActividad tipo, String busqueda, LocalDate fecha, TipoEstadoActividad estado,
                                    Boolean cupoDisponible, Pageable pageable);

  long count();
}
