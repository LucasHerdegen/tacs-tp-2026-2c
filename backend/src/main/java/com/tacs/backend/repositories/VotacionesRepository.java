package com.tacs.backend.repositories;

import com.tacs.backend.domain.votacion.Votacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VotacionesRepository extends JpaRepository<Votacion, Long> {
    List<Votacion> findByAbiertaAndActividadOrganizadorId(boolean abierta, Long organizadorId);
    List<Votacion> findByAbiertaAndActividadParticipantesId(boolean abierta, Long usuarioId);
    Optional<Votacion> findByAbiertaTrueAndActividadId(Long actividadId);
}
