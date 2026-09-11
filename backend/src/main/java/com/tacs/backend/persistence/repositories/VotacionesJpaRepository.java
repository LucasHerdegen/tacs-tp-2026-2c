package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.VotacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VotacionesJpaRepository extends JpaRepository<VotacionEntity, Long>
{
  List<VotacionEntity> findByAbiertaAndActividadOrganizadorId(boolean abierta, Long organizadorId);

  List<VotacionEntity> findByAbiertaAndActividadParticipantesId(boolean abierta, Long usuarioId);

  Optional<VotacionEntity> findByAbiertaTrueAndActividadId(Long actividadId);

  List<VotacionEntity> findByAbiertaTrueAndFechaLimiteBefore(LocalDateTime ahora);

  @Query("""
      SELECT DISTINCT v FROM VotacionEntity v
      LEFT JOIN v.actividad.participantes p
      WHERE v.abierta = :abierta
      AND (v.actividad.organizador.id = :usuarioId OR p.id = :usuarioId)
      """)
  List<VotacionEntity> findByAbiertaYUsuarioInvolucrado(boolean abierta, Long usuarioId);
}
