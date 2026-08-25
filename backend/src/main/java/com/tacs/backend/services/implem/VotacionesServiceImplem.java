package com.tacs.backend.services.implem;

import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.mappers.VotacionMapper;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.VotacionesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
class VotacionesServiceImplem implements VotacionesService {
    private final VotacionesRepository votacionesRepository;
    private final UsuarioRepository usuarioRepository;
    private final VotacionMapper votacionMapper;

    @Override
    public List<VotacionDto> votacionesAbiertas(Long usuarioId) {
        validarExistenciaUsuario(usuarioId);

        // TODO: asumo que el organizador no puede ser también participante de la misma
        // actividad. Si esa regla cambia, revisar duplicados (volver a un Set como antes)

        // Set<Votacion> votaciones = new LinkedHashSet<>();
        // votaciones.addAll(votacionesRepository.findByAbiertaTrueAndActividadOrganizadorId(usuarioId));
        // votaciones.addAll(votacionesRepository.findByAbiertaTrueAndActividadParticipantesId(usuarioId));

        List<Votacion> votaciones = new ArrayList<>();
        votaciones.addAll(votacionesRepository.findByAbiertaTrueAndActividadOrganizadorId(usuarioId));
        votaciones.addAll(votacionesRepository.findByAbiertaTrueAndActividadParticipantesId(usuarioId));

        return votaciones.stream()
                .map(votacionMapper::votacionToVotacionDto)
                .toList();
    }

    private void validarExistenciaUsuario(Long usuarioId) {
        if(!usuarioRepository.existsById(usuarioId))
            throw new UsuarioNotFoundException("El usuario con id: " + usuarioId + " no existe");
    }
}
