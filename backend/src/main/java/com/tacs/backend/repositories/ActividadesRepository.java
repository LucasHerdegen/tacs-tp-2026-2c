package com.tacs.backend.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ActividadesRepository extends JpaRepository<Actividad, Long>
{
  List<Actividad> findByOrganizadorId(Long organizadorId);
  List<Actividad> findByOrganizadorIdAndEstado(Long organizadorId, TipoEstadoActividad estado);
  List<Actividad> findByParticipantesId(Long usuarioId);
  List<Actividad> findByParticipantesIdAndEstado(Long usuarioId, TipoEstadoActividad estado);
  long countByEstado(TipoEstadoActividad estado);

  @Query("""
      SELECT a FROM Actividad a
      WHERE a.estado NOT IN (
          com.tacs.backend.domain.actividad.TipoEstadoActividad.CANCELADA,
          com.tacs.backend.domain.actividad.TipoEstadoActividad.FINALIZADA)
      AND a.reglasClima IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM Votacion v WHERE v.actividad = a AND v.abierta = true
      )
      """)
  List<Actividad> findCandidatasParaChequeoClima();


  @Query("""
    SELECT DISTINCT a FROM Actividad a
     LEFT JOIN FETCH a.participantes
     WHERE a.estado NOT IN (
         com.tacs.backend.domain.actividad.TipoEstadoActividad.CANCELADA,
         com.tacs.backend.domain.actividad.TipoEstadoActividad.FINALIZADA)
     AND a.recordatorioEnviado = false
     AND a.fechaRealizacion > CURRENT_TIMESTAMP""")
  List<Actividad> findCandidatasParaRecordatorio();
}