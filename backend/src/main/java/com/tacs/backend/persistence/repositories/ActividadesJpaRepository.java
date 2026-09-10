package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.persistence.entities.ActividadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActividadesJpaRepository extends JpaRepository<ActividadEntity, Long>
{
  List<ActividadEntity> findByOrganizadorId(Long organizadorId);

  List<ActividadEntity> findByOrganizadorIdAndEstado(Long organizadorId, TipoEstadoActividad estado);

  List<ActividadEntity> findByParticipantesId(Long usuarioId);

  List<ActividadEntity> findByParticipantesIdAndEstado(Long usuarioId, TipoEstadoActividad estado);

  @Query("SELECT DISTINCT a FROM ActividadEntity a LEFT JOIN a.participantes p WHERE a.organizador.id = :usuarioId OR p.id = :usuarioId")
  List<ActividadEntity> findByOrganizadorIdOrParticipantesId(Long usuarioId);

  @Query("SELECT DISTINCT a FROM ActividadEntity a LEFT JOIN a.participantes p WHERE (a.organizador.id = :usuarioId OR p.id = :usuarioId) AND a.estado = :estado")
  List<ActividadEntity> findByOrganizadorIdOrParticipantesIdAndEstado(Long usuarioId, TipoEstadoActividad estado);

  long countByEstado(TipoEstadoActividad estado);

  @Query("""
      SELECT a FROM ActividadEntity a
      WHERE a.estado NOT IN (
          TipoEstadoActividad.CANCELADA,
          TipoEstadoActividad.FINALIZADA)
      AND a.reglasClima IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM VotacionEntity v WHERE v.actividad = a AND v.abierta = true
      )
      """)
  List<ActividadEntity> findCandidatasParaChequeoClima();

  List<ActividadEntity> findByEstadoAndRecordatorioEnviadoFalse(TipoEstadoActividad estado);

  @Query("""
      SELECT DISTINCT a FROM ActividadEntity a
       LEFT JOIN FETCH a.participantes
       WHERE a.estado NOT IN (
           TipoEstadoActividad.CANCELADA,
           TipoEstadoActividad.FINALIZADA)
       AND a.recordatorioEnviado = false
       AND a.fechaRealizacion > CURRENT_TIMESTAMP""")
  List<ActividadEntity> findCandidatasParaRecordatorio();
}
