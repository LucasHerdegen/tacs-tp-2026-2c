package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.domain.votacion.Voto;
import com.tacs.backend.dtos.votacion.AlternativaPostDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.dtos.votacion.VotacionPostDto;
import com.tacs.backend.exceptions.AlternativaNotFoundException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.VotacionCerradaException;
import com.tacs.backend.exceptions.VotacionNotFoundException;
import com.tacs.backend.mappers.VotacionMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.VotacionesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
class VotacionesServiceImplem implements VotacionesService {
    private final VotacionesRepository votacionesRepository;
    private final ActividadesRepository actividadesRepository;
    private final UsuarioRepository usuarioRepository;
    private final VotacionMapper votacionMapper;
    private final ProveedorClima proveedorClima;

    @Override
    public List<VotacionDto> votaciones(Long usuarioId, boolean abierta) {
        validarExistenciaUsuario(usuarioId);

        // TODO: asumo que el organizador no puede ser también participante de la misma
        // actividad. Si esa regla cambia, revisar duplicados (volver a un Set como antes)

        // Set<Votacion> votaciones = new LinkedHashSet<>();
        // votaciones.addAll(votacionesRepository.findByAbiertaTrueAndActividadOrganizadorId(usuarioId));
        // votaciones.addAll(votacionesRepository.findByAbiertaTrueAndActividadParticipantesId(usuarioId));

        List<Votacion> votaciones = new ArrayList<>();
        votaciones.addAll(votacionesRepository.findByAbiertaAndActividadOrganizadorId(abierta, usuarioId));
        votaciones.addAll(votacionesRepository.findByAbiertaAndActividadParticipantesId(abierta, usuarioId));

        return votaciones.stream()
                .map(votacionMapper::votacionToVotacionDto)
                .toList();
    }

    @Override
    @Transactional
    public VotacionDto crearVotacion(Long actividadId, VotacionPostDto votacionPostDto) {
        Actividad actividad = actividadesRepository.findById(actividadId)
                .orElseThrow(() -> new IllegalArgumentException("Actividad no encontrada"));

        if (votacionesRepository.findByAbiertaTrueAndActividadId(actividadId).isPresent())
            throw new IllegalStateException("La actividad ya tiene una votacion abierta");

        Votacion votacion = new Votacion();
        votacion.setActividad(actividad);
        votacion.setFechaApertura(java.time.LocalDateTime.now());
        votacion.setQuorumMinimo(votacionPostDto.quorumMinimo());
        votacion.setAbierta(true);

        int numero = 1;
        for (AlternativaPostDto altDto : votacionPostDto.alternativas()) {
            votacion.agregarAlternativa(crearAlternativa(altDto, numero++, actividad));
        }

        votacion = votacionesRepository.save(votacion);
        return votacionMapper.votacionToVotacionDto(votacion);
    }

    @Override
    public VotacionDto obtenerVotacion(Long votacionId) {
        return votacionMapper.votacionToVotacionDto(buscarVotacion(votacionId));
    }

    @Override
    @Transactional
    public VotacionDto agregarAlternativa(Long votacionId, AlternativaPostDto alternativaPostDto) {
        Votacion votacion = buscarVotacion(votacionId);
        validarVotacionAbierta(votacion);

        int siguienteNumero = votacion.getAlternativas().stream()
                .mapToInt(Alternativa::getNumeroAltenativa)
                .max()
                .orElse(0) + 1;

        Alternativa alternativa = crearAlternativa(alternativaPostDto, siguienteNumero, votacion.getActividad());
        votacion.agregarAlternativa(alternativa);
        votacion = votacionesRepository.save(votacion);

        return votacionMapper.votacionToVotacionDto(votacion);
    }

    @Override
    @Transactional
    public void eliminarAlternativa(Long votacionId, int numeroAlternativa) {
        Votacion votacion = buscarVotacion(votacionId);
        validarVotacionAbierta(votacion);

        boolean existe = votacion.getAlternativas().stream()
                .anyMatch(a -> a.getNumeroAltenativa() == numeroAlternativa);

        if (!existe)
            throw new AlternativaNotFoundException("No existe la alternativa numero " + numeroAlternativa);

        votacion.eliminarAlternativa(numeroAlternativa);
        votacionesRepository.save(votacion);
    }

    /**
     * Registra (o actualiza, si ya habia votado antes) el voto de un participante.
     * Solo pueden votar quienes participan de la actividad asociada.
     */
    @Override
    @Transactional
    public VotacionDto votar(Long votacionId, Long usuarioId, int numeroAlternativa) {
        Votacion votacion = buscarVotacion(votacionId);
        validarVotacionAbierta(votacion);

        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

        boolean esParticipante = votacion.getActividad().getParticipantes().stream()
                .anyMatch(u -> u.getId().equals(usuarioId));

        if (!esParticipante)
            throw new IllegalStateException("Debes ser participante de la actividad para votar");

        Alternativa alternativa = votacion.getAlternativas().stream()
                .filter(a -> a.getNumeroAltenativa() == numeroAlternativa)
                .findFirst()
                .orElseThrow(() -> new AlternativaNotFoundException("No existe la alternativa numero " + numeroAlternativa));

        Voto voto = new Voto();
        voto.setUsuario(usuario);
        voto.setAlternativa(alternativa);

        votacion.registrarVoto(voto);
        votacion = votacionesRepository.save(votacion);

        return votacionMapper.votacionToVotacionDto(votacion);
    }

    @Override
    @Transactional
    public void eliminarVotacion(Long votacionId) {
        votacionesRepository.delete(buscarVotacion(votacionId));
    }

    private Votacion buscarVotacion(Long votacionId) {
        return votacionesRepository.findById(votacionId)
                .orElseThrow(() -> new VotacionNotFoundException("Votacion no encontrada"));
    }

    private void validarVotacionAbierta(Votacion votacion) {
        if (!votacion.isAbierta())
            throw new VotacionCerradaException("La votacion ya esta cerrada");
    }

    private Alternativa crearAlternativa(AlternativaPostDto dto, int numero, Actividad actividad) {
        Alternativa alternativa = new Alternativa();
        alternativa.setFecha(dto.fecha());
        alternativa.setNumeroAltenativa(numero);
        alternativa.setClima(proveedorClima.obtenerPronostico(actividad.getUbicacion(), dto.fecha()));
        return alternativa;
    }

    private void validarExistenciaUsuario(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId))
            throw new UsuarioNotFoundException("El usuario con id: " + usuarioId + " no existe");
    }
}