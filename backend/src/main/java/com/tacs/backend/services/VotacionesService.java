package com.tacs.backend.services;

import com.tacs.backend.dtos.votacion.VotacionDto;

import java.util.List;

public interface VotacionesService {
    List<VotacionDto> votacionesAbiertas(Long usuarioId);
}
