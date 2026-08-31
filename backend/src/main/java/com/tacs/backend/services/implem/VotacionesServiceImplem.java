package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.RangoReprogramacion;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.domain.votacion.Voto;
import com.tacs.backend.dtos.votacion.AlternativaPostDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.dtos.votacion.VotacionPostDto;
import com.tacs.backend.exceptions.AlternativaNotFoundException;
import com.tacs.backend.exceptions.QuorumInvalidoException;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
class VotacionesServiceImplem implements VotacionesService {
    private static final int GRANULARIDAD_BUSQUEDA_HORAS = 2;

    private final VotacionesRepository votacionesRepository;
    private final ActividadesRepository actividadesRepository;
    private final UsuarioRepository usuarioRepository;
    private final VotacionMapper votacionMapper;
    private final ProveedorClima proveedorClima;

    // ==================== CRUD / metodos publicos (ver Javadoc en VotacionesService) ====================

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
        Actividad actividad = buscarActividad(actividadId);

        validarSinVotacionAbierta(actividadId);
        validarQuorumMinimo(votacionPostDto.quorumMinimo(), actividad);

        int numero = 1;
        List<Alternativa> alternativas = new ArrayList<>();

        for (AlternativaPostDto altDto : votacionPostDto.alternativas())
            alternativas.add(crearAlternativa(altDto, numero++, actividad));

        Votacion votacion = abrirVotacion(actividad, votacionPostDto.quorumMinimo(), votacionPostDto.fechaLimite(), alternativas);
        return votacionMapper.votacionToVotacionDto(votacion);
    }

    @Override
    @Transactional
    public Optional<VotacionDto> abrirVotacionAutomatica(Long actividadId) {
        Actividad actividad = buscarActividad(actividadId);

        validarSinVotacionAbierta(actividadId);

        List<Alternativa> alternativasFavorables = buscarAlternativasFavorables(actividad);

        if (alternativasFavorables.isEmpty()) {
            cancelarActividad(actividad);
            actividadesRepository.save(actividad);
            return Optional.empty();
        }

        Votacion votacion = abrirVotacion(actividad, actividad.getMinimoParticipantes(), calcularFechaLimite(actividad), alternativasFavorables);
        return Optional.of(votacionMapper.votacionToVotacionDto(votacion));
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
    public VotacionDto resolverVotacion(Long votacionId) {
        Votacion votacion = buscarVotacion(votacionId);
        validarVotacionAbierta(votacion);

        Optional<Alternativa> ganadora = votacion.alternativaMasVotada()
                .filter(alternativa -> votacion.cantidadVotos(alternativa) >= votacion.getQuorumMinimo());

        Actividad actividad = votacion.getActividad();

        if (ganadora.isPresent())
            actividad.reprogramar(ganadora.get().getFecha());
        else
            cancelarActividad(actividad);

        actividadesRepository.save(actividad);

        votacion.cerrar(ganadora.orElse(null));
        Votacion votacionCerrada = votacionesRepository.save(votacion);

        return votacionMapper.votacionToVotacionDto(votacionCerrada);
    }

    @Override
    @Transactional
    public void eliminarVotacion(Long votacionId) {
        votacionesRepository.delete(buscarVotacion(votacionId));
    }

    // ==================== Metodos auxiliares ====================

    private void validarExistenciaUsuario(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId))
            throw new UsuarioNotFoundException("El usuario con id: " + usuarioId + " no existe");
    }

    private Actividad buscarActividad(Long actividadId) {
        return actividadesRepository.findById(actividadId)
                .orElseThrow(() -> new IllegalArgumentException("Actividad no encontrada"));
    }

    private void validarSinVotacionAbierta(Long actividadId) {
        if (votacionesRepository.findByAbiertaTrueAndActividadId(actividadId).isPresent())
            throw new IllegalStateException("La actividad ya tiene una votacion abierta");
    }

    private void validarQuorumMinimo(int quorumMinimo, Actividad actividad) {
        if (quorumMinimo < actividad.getMinimoParticipantes())
            throw new QuorumInvalidoException(
                "El quorum minimo (%d) no puede ser menor a la cantidad minima de participantes de la actividad (%d)"
                    .formatted(quorumMinimo, actividad.getMinimoParticipantes()));
    }

    private Alternativa crearAlternativa(AlternativaPostDto dto, int numero, Actividad actividad) {
        Clima pronostico = proveedorClima.obtenerPronostico(actividad.getUbicacion(), dto.fecha());
        return construirAlternativa(dto.fecha(), numero, pronostico);
    }

    private Votacion abrirVotacion(Actividad actividad, int quorumMinimo, LocalDateTime fechaLimite, List<Alternativa> alternativas) {
        Votacion votacion = new Votacion();
        votacion.setActividad(actividad);
        votacion.setFechaApertura(LocalDateTime.now());
        votacion.setFechaLimite(fechaLimite);
        votacion.setQuorumMinimo(quorumMinimo);
        votacion.setAbierta(true);
        alternativas.forEach(votacion::agregarAlternativa);

        return votacionesRepository.save(votacion);
    }

    /**
     * Busca, dentro de rangoReprogramacion (dias permitidos y franja horaria
     * horaInicio-horaFinal definidos por el organizador), todas las horas de
     * cada dia que cumplan las ReglasClima de la actividad: cada una se ofrece
     * como alternativa propia, no solo la de mejor pronostico del dia (elegir
     * entre varias opciones viables es trabajo de la votacion, no del sistema).
     * Sin rangoReprogramacion configurado no hay donde buscar, y se devuelve
     * vacio (la actividad termina cancelandose, ver abrirVotacionAutomatica).
     */
    private List<Alternativa> buscarAlternativasFavorables(Actividad actividad) {
        RangoReprogramacion rango = actividad.getRangoReprogramacion();

        if (rango == null)
            return List.of();

        List<Alternativa> favorables = new ArrayList<>();
        int numero = 1;

        for (int dia = 1; dia <= rango.getDias(); dia++)
            for (Alternativa alternativa : alternativasFavorablesDelDia(actividad, rango, dia)) {
                alternativa.setNumeroAltenativa(numero++);
                favorables.add(alternativa);
            }

        return favorables;
    }

    /**
     * Recorre la franja horaInicio-horaFinal de ese dia cada
     * GRANULARIDAD_BUSQUEDA_HORAS horas y devuelve todas las alternativas que cumplen las
     * ReglasClima (numero sin asignar todavia, se numera al aplanar en
     * buscarAlternativasFavorables). Vacia si ninguna cumple.
     */
    private List<Alternativa> alternativasFavorablesDelDia(Actividad actividad, RangoReprogramacion rango, int dia) {
        LocalDateTime diaCandidato = actividad.getFecha().plusDays(dia);
        List<Alternativa> favorablesDelDia = new ArrayList<>();

        for (int hora = rango.getHoraInicio(); hora <= rango.getHoraFinal(); hora += GRANULARIDAD_BUSQUEDA_HORAS) {
            LocalDateTime fechaCandidata = diaCandidato.withHour(hora).withMinute(0).withSecond(0).withNano(0);

            Clima pronostico = proveedorClima.obtenerPronostico(actividad.getUbicacion(), fechaCandidata);

            if (actividad.cumpleCondiciones(pronostico))
                favorablesDelDia.add(construirAlternativa(fechaCandidata, 0, pronostico));
        }

        return favorablesDelDia;
    }

    private void cancelarActividad(Actividad actividad) {
        if (actividad.getEstado() == null)
            throw new IllegalStateException("La actividad id=" + actividad.getId() + " no tiene un estado configurado, no se puede cancelar");

        // TODO: Notificar cuando se cancela al organizador?
        actividad.getEstado().cambiarEstado(actividad, TipoEstadoActividad.CANCELADA);
    }

    /**
     * 1/2 del tiempo restante hasta la fecha original de la actividad,
     * dejando margen para votar y para que el resultado se conozca antes de
     * esa fecha. Si la fecha original ya esta encima (o paso), usa un margen
     * minimo fijo en vez de una fechaLimite invalida (pasada o inmediata).
     *
     * NOTA - Esto es cuestionable si por ej. el CRON ejecutase a las 23hs de un
     * viernes por una actividad del sabado a las 23hs, practicamente no
     * habria tiempo para votar. Se podria modificar simplemente cambiando este metodo
     */
    private LocalDateTime calcularFechaLimite(Actividad actividad) {
        LocalDateTime ahora = LocalDateTime.now();
        Duration restante = Duration.between(ahora, actividad.getFecha());

        if (restante.isNegative() || restante.isZero())
            return ahora.plusHours(1);

        return ahora.plus(restante.dividedBy(2));
    }

    private Votacion buscarVotacion(Long votacionId) {
        return votacionesRepository.findById(votacionId)
                .orElseThrow(() -> new VotacionNotFoundException("Votacion no encontrada"));
    }

    private void validarVotacionAbierta(Votacion votacion) {
        if (!votacion.isAbierta())
            throw new VotacionCerradaException("La votacion ya esta cerrada");
    }

    // Helper utilizado por crearAlternativa (fecha manual, via DTO)
    // y mejorHorarioDelDia (fecha calculada por la busqueda automatica).
    private Alternativa construirAlternativa(LocalDateTime fecha, int numero, Clima clima) {
        Alternativa alternativa = new Alternativa();
        alternativa.setFecha(fecha);
        alternativa.setNumeroAltenativa(numero);
        alternativa.setClima(clima);
        return alternativa;
    }
}
