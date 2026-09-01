package com.tacs.backend.repositories;

import com.tacs.backend.domain.votacion.Votacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VotacionesRepository extends JpaRepository<Votacion, Long> {
    List<Votacion> findByAbiertaAndActividadOrganizadorId(boolean abierta, Long organizadorId);
    List<Votacion> findByAbiertaAndActividadParticipantesId(boolean abierta, Long usuarioId);
    Optional<Votacion> findByAbiertaTrueAndActividadId(Long actividadId);
    List<Votacion> findByAbiertaTrueAndFechaLimiteBefore(LocalDateTime ahora);
    @Query("""
        SELECT DISTINCT v FROM Votacion v
        LEFT JOIN v.actividad.participantes p
        WHERE v.abierta = :abierta
        AND (v.actividad.organizador.id = :usuarioId OR p.id = :usuarioId)
        """)
    List<Votacion> findByAbiertaYUsuarioInvolucrado(boolean abierta, Long usuarioId);
}
