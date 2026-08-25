package com.tacs.backend.repositories;

import com.tacs.backend.domain.votacion.Votacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VotacionesRepository extends JpaRepository<Votacion, Long> {
    List<Votacion> findByAbiertaTrueAndActividadOrganizadorId(Long organizadorId);
    List<Votacion> findByAbiertaTrueAndActividadParticipantesId(Long usuarioId);
}
