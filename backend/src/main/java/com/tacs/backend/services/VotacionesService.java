package com.tacs.backend.services;

import com.tacs.backend.dtos.votacion.AlternativaPostDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.dtos.votacion.VotacionPostDto;

import java.util.List;

public interface VotacionesService {
    List<VotacionDto> votaciones(Long usuarioId, boolean abierta);

    VotacionDto crearVotacion(Long actividadId, VotacionPostDto votacionPostDto);

    VotacionDto obtenerVotacion(Long votacionId);

    VotacionDto agregarAlternativa(Long votacionId, AlternativaPostDto alternativaPostDto);

    void eliminarAlternativa(Long votacionId, int numeroAlternativa);

    VotacionDto votar(Long votacionId, Long usuarioId, int numeroAlternativa);

    void eliminarVotacion(Long votacionId);
}