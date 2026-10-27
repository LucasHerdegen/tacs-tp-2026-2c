package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.RangoReprogramacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.domain.notificacion.TipoNotificacion;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.domain.votacion.Voto;
import com.tacs.backend.dtos.votacion.AlternativaPostDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.dtos.votacion.VotacionPostDto;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.AlternativaNotFoundException;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.QuorumInvalidoException;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.VotacionCerradaException;
import com.tacs.backend.exceptions.VotacionNotFoundException;
import com.tacs.backend.mappers.VotacionMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.NotificacionInboxService;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import com.tacs.backend.services.VotacionesService;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class VotacionesServiceImplem implements VotacionesService
{
  private static final int GRANULARIDAD_BUSQUEDA_HORAS = 2;
  private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd:MM:yyyy HH:mm");

  private final VotacionesRepository votacionesRepository;
  private final ActividadesRepository actividadesRepository;
  private final UsuarioRepository usuarioRepository;
  private final VotacionMapper votacionMapper;
  private final ProveedorClima proveedorClima;
  private final ServicioNotificaciones servicioNotificaciones;
  private final NotificacionInboxService notificacionInboxService;
  private final OptimisticLockingRetry optimisticLockingRetry;
  private final ActividadesService actividadesService;

  @Override
  public List<VotacionDto> votaciones(String usuarioId, boolean abierta)
  {
    validarExistenciaUsuario(usuarioId);

    return votacionesRepository.findByAbiertaYUsuarioInvolucrado(abierta, usuarioId).stream()
        .map(votacionMapper::votacionToVotacionDto)
        .toList();
  }

  @Override
  public VotacionDto crearVotacion(String actividadId, VotacionPostDto votacionPostDto, String usuarioId)
  {
    Actividad actividad = buscarActividad(actividadId);
    actividad.validarPermiteAbrirVotacion();

    validarOrganizador(actividad, usuarioId);
    validarSinVotacionAbierta(actividadId);
    validarQuorumMinimo(votacionPostDto.quorumMinimo(), actividad);

    if (votacionPostDto.fechaLimite() != null && votacionPostDto.fechaLimite().isAfter(actividad.getFechaRealizacion()))
      throw new IllegalArgumentException(
          "La fecha límite de la votación no puede ser posterior a la fecha de la actividad");

    int numero = 1;
    List<Alternativa> alternativas = new ArrayList<>();

    for (AlternativaPostDto altDto : votacionPostDto.alternativas())
      alternativas.add(crearAlternativa(altDto, numero++, actividad));

    Votacion votacion = abrirVotacion(actividad, votacionPostDto.quorumMinimo(), votacionPostDto.fechaLimite(),
        alternativas);
    return votacionMapper.votacionToVotacionDto(votacion);
  }

  @Override
  public Optional<VotacionDto> abrirVotacionAutomatica(String actividadId)
  {
    Actividad actividad = buscarActividad(actividadId);
    actividad.validarPermiteAbrirVotacion();

    validarSinVotacionAbierta(actividadId);

    ResultadoBusquedaAlternativas resultado = buscarAlternativasFavorables(actividad);

    if (resultado.favorables().isEmpty())
    {
      if (resultado.climaIndisponible())
        return Optional.empty();

      actividadesService.cancelarActividad(actividad, "no se encuentran fechas alternaticas con buen pronostico", null);
      return Optional.empty();
    }

    Votacion votacion = abrirVotacion(actividad, actividad.getMinimoParticipantes(), calcularFechaLimite(actividad),
        resultado.favorables());
    return Optional.of(votacionMapper.votacionToVotacionDto(votacion));
  }

  @Override
  public VotacionDto obtenerVotacion(String votacionId)
  {
    return votacionMapper.votacionToVotacionDto(buscarVotacion(votacionId));
  }

  @Override
  public VotacionDto agregarAlternativa(String votacionId, AlternativaPostDto alternativaPostDto, String usuarioId)
  {
    Votacion votacion = buscarVotacion(votacionId);
    validarOrganizador(votacion.getActividad(), usuarioId);
    validarVotacionAbierta(votacion);

    int siguienteNumero = votacion.getAlternativas().stream()
        .mapToInt(Alternativa::getNumeroAlternativa)
        .max()
        .orElse(0) + 1;

    Alternativa alternativa = crearAlternativa(alternativaPostDto, siguienteNumero, votacion.getActividad());
    votacion.agregarAlternativa(alternativa);
    votacion = votacionesRepository.save(votacion);

    return votacionMapper.votacionToVotacionDto(votacion);
  }

  @Override
  public void eliminarAlternativa(String votacionId, int numeroAlternativa, String usuarioId)
  {
    Votacion votacion = buscarVotacion(votacionId);
    validarOrganizador(votacion.getActividad(), usuarioId);
    validarVotacionAbierta(votacion);

    boolean existe = votacion.getAlternativas().stream()
        .anyMatch(a -> a.getNumeroAlternativa() == numeroAlternativa);

    if (!existe)
      throw new AlternativaNotFoundException("No existe la alternativa numero " + numeroAlternativa);

    votacion.eliminarAlternativa(numeroAlternativa);
    votacionesRepository.save(votacion);
  }

  @Override
  public VotacionDto votar(String votacionId, String usuarioId, int numeroAlternativa)
  {
    return optimisticLockingRetry.ejecutarConReintento(() ->
    {
      Votacion votacion = buscarVotacion(votacionId);
      validarVotacionAbierta(votacion);

      var usuario = usuarioRepository.findById(usuarioId)
          .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

      boolean esParticipante = votacion.getActividad().getParticipantes().stream()
          .anyMatch(u -> u.getId().equals(usuarioId));

      if (!esParticipante)
        throw new NoParticipanteException("Debes ser participante de la actividad para votar");

      Alternativa alternativa = votacion.getAlternativas().stream()
          .filter(a -> a.getNumeroAlternativa() == numeroAlternativa)
          .findFirst()
          .orElseThrow(() -> new AlternativaNotFoundException("No existe la alternativa numero " + numeroAlternativa));

      Voto voto = new Voto();
      voto.setUsuario(usuario);
      voto.setAlternativa(alternativa);

      votacion.registrarVoto(voto);
      votacion = votacionesRepository.save(votacion);

      return votacionMapper.votacionToVotacionDto(votacion);
    });
  }

  @Override
  public Optional<Integer> alternativaVotadaPor(String votacionId, String usuarioId)
  {
    Votacion votacion = buscarVotacion(votacionId);

    return votacion.getVotos().stream()
        .filter(v -> v.getUsuario().getId().equals(usuarioId))
        .map(v -> v.getAlternativa().getNumeroAlternativa())
        .findFirst();
  }

  @Override
  public VotacionDto resolverVotacion(String votacionId, String usuarioId)
  {
    Votacion votacion = buscarVotacion(votacionId);
    validarOrganizador(votacion.getActividad(), usuarioId);
    return resolverVotacionInterno(votacion);
  }

  @Override
  public VotacionDto resolverVotacion(String votacionId)
  {
    return resolverVotacionInterno(buscarVotacion(votacionId));
  }

  private VotacionDto resolverVotacionInterno(Votacion votacion)
  {
    validarVotacionAbierta(votacion);

    Actividad actividad = votacion.getActividad();
    if (actividad.getEstado() == null)
      throw new IllegalStateException(
          "La actividad id=" + actividad.getId() + " no tiene un estado configurado, no se puede cancelar");
    actividad.validarPermiteAbrirVotacion();

    Optional<Alternativa> ganadora = votacion.alternativaMasVotada()
        .filter(alternativa -> votacion.cantidadVotos(alternativa) >= votacion.getQuorumMinimo());

    votacion.cerrar(ganadora.orElse(null));
    Votacion votacionCerrada = votacionesRepository.cerrarSiAbierta(votacion)
        .orElseThrow(() -> new VotacionCerradaException("La votacion ya esta cerrada"));

    if (ganadora.isPresent())
    {
      LocalDateTime fechaAnterior = actividad.getFechaRealizacion();
      actividad.reprogramar(ganadora.get().getFecha());
      actividadesRepository.save(actividad);
      notificarReprogramacion(actividad, fechaAnterior, votacion.getId());
    } else
      actividadesService.cancelarActividad(actividad, "No me alcanzo el quorum minimo de votos", votacion.getId());

    return votacionMapper.votacionToVotacionDto(votacionCerrada);
  }

  @Override
  public void eliminarVotacion(String votacionId, String usuarioId)
  {
    Votacion votacion = buscarVotacion(votacionId);
    validarOrganizador(votacion.getActividad(), usuarioId);
    votacionesRepository.delete(votacion);
  }

  private void validarOrganizador(Actividad actividad, String usuarioId)
  {
    if (!actividad.getOrganizador().getId().equals(usuarioId))
      throw new AccesoDenegadoException("Solo el organizador puede realizar esta acción");
  }

  private void validarExistenciaUsuario(String usuarioId)
  {
    if (!usuarioRepository.existsById(usuarioId))
      throw new UsuarioNotFoundException("El usuario con id: " + usuarioId + " no existe");
  }

  private Actividad buscarActividad(String actividadId)
  {
    return actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));
  }

  private void validarSinVotacionAbierta(String actividadId)
  {
    if (votacionesRepository.findByAbiertaTrueAndActividadId(actividadId).isPresent())
      throw new IllegalStateException("La actividad ya tiene una votacion abierta");
  }

  private void validarQuorumMinimo(int quorumMinimo, Actividad actividad)
  {
    if (quorumMinimo < actividad.getMinimoParticipantes())
      throw new QuorumInvalidoException(
          "El quorum minimo (%d) no puede ser menor a la cantidad minima de participantes de la actividad (%d)"
              .formatted(quorumMinimo, actividad.getMinimoParticipantes()));
  }

  private Alternativa crearAlternativa(AlternativaPostDto dto, int numero, Actividad actividad)
  {
    if (actividad.getRangoReprogramacion() == null || !actividad.getRangoReprogramacion()
        .contiene(actividad.getFechaRealizacion(), dto.fecha()))
      throw new RangoReprogramacionInvalidoException(
          "La fecha de la alternativa debe estar dentro del rango de reprogramacion permitido por la actividad");

    Clima pronostico;
    iniciarContextoOrganizador(actividad);
    try
    {
      pronostico = proveedorClima.obtenerPronostico(actividad.getUbicacion(), dto.fecha());
    } catch (ProveedorClimaIndisponibleException e)
    {
      pronostico = null;
    } finally
    {
      ContextoProveedorClima.limpiar();
    }

    return construirAlternativa(dto.fecha(), numero, pronostico);
  }

  private Votacion abrirVotacion(
      Actividad actividad,
      int quorumMinimo,
      LocalDateTime fechaLimite,
      List<Alternativa> alternativas)
  {
    actividad.validarPermiteAbrirVotacion();
    Votacion votacion = new Votacion();
    votacion.setActividad(actividad);
    votacion.setFechaApertura(LocalDateTime.now());
    votacion.setFechaLimite(fechaLimite);
    votacion.setQuorumMinimo(quorumMinimo);
    votacion.setAbierta(true);
    alternativas.forEach(votacion::agregarAlternativa);

    return votacionesRepository.save(votacion);
  }

  private record ResultadoBusquedaAlternativas(List<Alternativa> favorables, boolean climaIndisponible)
  {
  }

  private ResultadoBusquedaAlternativas buscarAlternativasFavorables(Actividad actividad)
  {
    RangoReprogramacion rango = actividad.getRangoReprogramacion();

    if (rango == null)
      return new ResultadoBusquedaAlternativas(List.of(), false);

    List<Alternativa> favorables = new ArrayList<>();
    boolean algunaConsultaExitosa = false;
    int numero = 1;

    for (int dia = 1; dia <= rango.getDias(); dia++)
    {
      ResultadoBusquedaDia resultadoDia = alternativasFavorablesDelDia(actividad, rango, dia);
      algunaConsultaExitosa = algunaConsultaExitosa || resultadoDia.huboConsultaExitosa();

      for (Alternativa alternativa : resultadoDia.favorables())
      {
        alternativa.setNumeroAlternativa(numero++);
        favorables.add(alternativa);
      }
    }

    return new ResultadoBusquedaAlternativas(favorables, !algunaConsultaExitosa);
  }

  private record ResultadoBusquedaDia(List<Alternativa> favorables, boolean huboConsultaExitosa)
  {
  }

  private ResultadoBusquedaDia alternativasFavorablesDelDia(Actividad actividad, RangoReprogramacion rango, int dia)
  {
    LocalDateTime diaCandidato = actividad.getFechaRealizacion().plusDays(dia);
    List<Alternativa> favorablesDelDia = new ArrayList<>();
    boolean huboConsultaExitosa = false;

    iniciarContextoOrganizador(actividad);
    try
    {
      for (int hora = rango.getHoraInicio(); hora <= rango.getHoraFinal(); hora += GRANULARIDAD_BUSQUEDA_HORAS)
      {
        LocalDateTime fechaCandidata = diaCandidato.withHour(hora).withMinute(0).withSecond(0).withNano(0);

        Clima pronostico;
        try
        {
          pronostico = proveedorClima.obtenerPronostico(actividad.getUbicacion(), fechaCandidata);
        } catch (ProveedorClimaIndisponibleException e)
        {
          continue;
        }

        huboConsultaExitosa = true;
        if (actividad.cumpleCondiciones(pronostico))
          favorablesDelDia.add(construirAlternativa(fechaCandidata, 0, pronostico));
      }
    } finally
    {
      ContextoProveedorClima.limpiar();
    }

    return new ResultadoBusquedaDia(favorablesDelDia, huboConsultaExitosa);
  }

  private void iniciarContextoOrganizador(Actividad actividad)
  {
    Usuario organizador = actividad != null ? actividad.getOrganizador() : null;
    String organizadorId = organizador != null ? organizador.getId() : null;
    TipoProveedorClima proveedorOrganizador = organizador != null
        ? organizador.getProveedorClimaEfectivo()
        : TipoProveedorClima.WEATHER_API;
    ContextoProveedorClima.iniciar(organizadorId, proveedorOrganizador);
  }

  private void notificarReprogramacion(Actividad actividad, LocalDateTime fechaAnterior, String votacionId)
  {
    String contenido = "La actividad '%s' se reprogramo del %s al %s.".formatted(
        actividad.getTitulo(),
        fechaAnterior.format(FORMATO),
        actividad.getFechaRealizacion().format(FORMATO));

    servicioNotificaciones.notificarATodos(contenido, actividad.getParticipantes());
    notificacionInboxService.crearParaTodos(contenido, TipoNotificacion.REPROGRAMACION,
        actividad.getId(), votacionId, actividad.getParticipantes());
  }

  private LocalDateTime calcularFechaLimite(Actividad actividad)
  {
    LocalDateTime ahora = LocalDateTime.now();
    Duration restante = Duration.between(ahora, actividad.getFechaRealizacion());

    if (restante.isNegative() || restante.isZero())
      return ahora.plusHours(1);

    return ahora.plus(restante.dividedBy(2));
  }

  private Votacion buscarVotacion(String votacionId)
  {
    return votacionesRepository.findById(votacionId)
        .orElseThrow(() -> new VotacionNotFoundException("Votacion no encontrada"));
  }

  private void validarVotacionAbierta(Votacion votacion)
  {
    if (!votacion.isAbierta())
      throw new VotacionCerradaException("La votacion ya esta cerrada");
  }

  private Alternativa construirAlternativa(LocalDateTime fecha, int numero, Clima clima)
  {
    Alternativa alternativa = new Alternativa();
    alternativa.setId(new ObjectId().toHexString());
    alternativa.setFecha(fecha);
    alternativa.setNumeroAlternativa(numero);
    alternativa.setClima(clima);
    return alternativa;
  }
}
