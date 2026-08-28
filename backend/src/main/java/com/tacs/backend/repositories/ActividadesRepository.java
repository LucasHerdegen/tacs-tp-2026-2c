package com.tacs.backend.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ActividadesRepository extends JpaRepository<Actividad, Long>
{
  List<Actividad> findByOrganizadorId(Long organizadorId);
  List<Actividad> findByOrganizadorIdAndEstadoTipo(Long organizadorId, TipoEstadoActividad tipo);
  List<Actividad> findByParticipantesId(Long usuarioId);
  List<Actividad> findByParticipantesIdAndEstadoTipo(Long usuarioId, TipoEstadoActividad tipo);

  @Query("""
      SELECT a FROM Actividad a
      WHERE a.estado.tipo NOT IN (
          com.tacs.backend.domain.actividad.TipoEstadoActividad.CANCELADA,
          com.tacs.backend.domain.actividad.TipoEstadoActividad.FINALIZADA)
      AND a.reglasClima IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM Votacion v WHERE v.actividad = a AND v.abierta = true
      )
      """)
  List<Actividad> findCandidatasParaChequeoClima();
}