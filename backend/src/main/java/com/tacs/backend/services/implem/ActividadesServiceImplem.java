package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.domain.notificacion.TipoNotificacion;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.dtos.clima.ClimaDto;
import com.tacs.backend.dtos.clima.PronosticoRespuestaDto;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.VotacionCerradaException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.mappers.ClimaMapper;
import com.tacs.backend.observabilidad.ObservabilidadBusquedas;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.ValidacionOrdenamiento;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.NotificacionInboxService;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima.DatosContexto;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ActividadesServiceImplem implements ActividadesService
{
  private final ActividadesRepository actividadesRepository;
  private final UsuarioRepository usuarioRepository;
  private final ActividadesMapper actividadesMapper;
  private final ProveedorClima proveedorClima;
  private final ClimaMapper climaMapper;
  private final ServicioNotificaciones servicioNotificaciones;
  private final NotificacionInboxService notificacionInboxService;
  private final VotacionesRepository votacionesRepository;
  private final OptimisticLockingRetry optimisticLockingRetry;
  private final ObservabilidadBusquedas observabilidadBusquedas;

  @Value("${weatherapi.forecast.max-days:3}")
  private int maxDiasForecast;

  @Autowired(required = false)
  public ActividadesServiceImplem(
      ActividadesRepository actividadesRepository,
      UsuarioRepository usuarioRepository,
      ActividadesMapper actividadesMapper,
      ProveedorClima proveedorClima,
      ClimaMapper climaMapper,
      ServicioNotificaciones servicioNotificaciones,
      NotificacionInboxService notificacionInboxService,
      VotacionesRepository votacionesRepository,
      OptimisticLockingRetry optimisticLockingRetry,
      ObservabilidadBusquedas observabilidadBusquedas)
  {
    this.actividadesRepository = actividadesRepository;
    this.usuarioRepository = usuarioRepository;
    this.actividadesMapper = actividadesMapper;
    this.proveedorClima = proveedorClima;
    this.climaMapper = climaMapper;
    this.servicioNotificaciones = servicioNotificaciones;
    this.notificacionInboxService = notificacionInboxService;
    this.votacionesRepository = votacionesRepository;
    this.optimisticLockingRetry = optimisticLockingRetry;
    this.observabilidadBusquedas = observabilidadBusquedas != null
        ? observabilidadBusquedas
        : new ObservabilidadBusquedas(OpenTelemetry.noop());
  }

  @Autowired(required = false)
  public ActividadesServiceImplem(
      ActividadesRepository actividadesRepository,
      UsuarioRepository usuarioRepository,
      ActividadesMapper actividadesMapper,
      ProveedorClima proveedorClima,
      ClimaMapper climaMapper,
      ServicioNotificaciones servicioNotificaciones,
      NotificacionInboxService notificacionInboxService,
      VotacionesRepository votacionesRepository,
      OptimisticLockingRetry optimisticLockingRetry)
  {
    this(
        actividadesRepository,
        usuarioRepository,
        actividadesMapper,
        proveedorClima,
        climaMapper,
        servicioNotificaciones,
        notificacionInboxService,
        votacionesRepository,
        optimisticLockingRetry,
        new ObservabilidadBusquedas(OpenTelemetry.noop())
    );
  }

  /**
   * Crea una nueva actividad propuesta por el usuario especificado.
   *
   * @param actividadPostDto DTO con los detalles de la nueva actividad.
   * @param usuarioId        ID del usuario que organiza la actividad.
   * @return DTO de la actividad creada.
   */
  @Override
  public ActividadDto createActividad(ActividadPostDto actividadPostDto, String usuarioId)
  {
    if (actividadPostDto.cantidadMinima() > actividadPostDto.cantidadMaxima())
      throw new IllegalArgumentException("La cantidad mínima no puede ser mayor a la máxima");

    var usuario = this.usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new UsuarioNotFoundException("El usuario con id " + usuarioId + " no fue encontrado"));

    var actividad = this.actividadesMapper.actividadPostDtoToActividad(actividadPostDto, usuario);

    actividad.cambiarEstado(TipoEstadoActividad.PROPUESTA);

    actividad = this.actividadesRepository.save(actividad);

    return this.actividadesMapper.actividadToActividadDto(actividad);
  }

  @Override
  public List<ActividadDto> actividadesDelUsuario(String usuarioId, TipoEstadoActividad estado)
  {
    validarExistenciaUsuario(usuarioId);

    List<Actividad> actividades = estado != null
        ? actividadesRepository.findByOrganizadorIdOrParticipantesIdAndEstado(usuarioId, estado)
        : actividadesRepository.findByOrganizadorIdOrParticipantesId(usuarioId);

    return actividades.stream()
        .map(actividadesMapper::actividadToActividadDto)
        .toList();
  }

  @Override
  public List<ActividadDto> actividadesOrganizadas(String usuarioId, TipoEstadoActividad estado)
  {
    validarExistenciaUsuario(usuarioId);

    List<Actividad> actividades = estado != null
        ? actividadesRepository.findByOrganizadorIdAndEstado(usuarioId, estado)
        : actividadesRepository.findByOrganizadorId(usuarioId);

    return actividades.stream()
        .map(actividadesMapper::actividadToActividadDto)
        .toList();
  }

  @Override
  public List<ActividadDto> actividadesParticipadas(String usuarioId, TipoEstadoActividad estado)
  {
    validarExistenciaUsuario(usuarioId);

    List<Actividad> actividades = estado != null
        ? actividadesRepository.findByParticipantesIdAndEstado(usuarioId, estado)
        : actividadesRepository.findByParticipantesId(usuarioId);

    return actividades.stream()
        .map(actividadesMapper::actividadToActividadDto)
        .toList();
  }

  private void validarExistenciaUsuario(String usuarioId)
  {
    if (!usuarioRepository.existsById(usuarioId))
      throw new UsuarioNotFoundException("El usuario con id: " + usuarioId + " no existe");
  }

  @Override
  public ActividadDto obtenerActividad(String id)
  {
    Actividad actividad = actividadesRepository.findById(id)
        .orElseThrow(() -> new ActividadNotFoundException(
            "Actividad con ID " + id + " no encontrada"));
    return actividadesMapper.actividadToActividadDto(actividad);
  }

  @Override
  public Page<ActividadDto> buscarActividades(
      TipoActividad tipo,
      String busqueda,
      LocalDate fechaInicio,
      LocalDate fechaFin,
      TipoEstadoActividad estado,
      Boolean cupoDisponible,
      Pageable pageable)
  {
    return observabilidadBusquedas.instrumentarBusqueda(
        tipo,
        busqueda,
        fechaInicio,
        fechaFin,
        estado,
        cupoDisponible,
        pageable,
        () ->
        {
          if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin))
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin");

          ValidacionOrdenamiento.validarActividades(pageable);

          Page<Actividad> actividades = actividadesRepository.buscarActividades(
              tipo, busqueda, fechaInicio, fechaFin, estado, cupoDisponible, pageable);

          return actividades.map(actividadesMapper::actividadToActividadDto);
        });
  }

  /**
   * Agrega a un usuario como participante de una actividad si no se ha alcanzado
   * la capacidad maxima.
   *
   * @param actividadId Identificador de la actividad.
   * @param usuarioId   Identificador del usuario que desea unirse.
   */
  @Override
  public void unirseActividad(String actividadId, String usuarioId)
  {
    validarExistenciaUsuario(usuarioId);

    var usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

    optimisticLockingRetry.ejecutarConReintento(() ->
    {
      var actividad = actividadesRepository.findById(actividadId)
          .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));

      actividad.agregarParticipante(usuario);
      actividadesRepository.save(actividad);
    });
  }

  /**
   * Remueve a un usuario de la lista de participantes de una actividad.
   *
   * @param actividadId Identificador de la actividad.
   * @param usuarioId   Identificador del usuario que desea bajarse.
   */
  @Override
  public void bajarseActividad(String actividadId, String usuarioId)
  {
    validarExistenciaUsuario(usuarioId);

    var usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

    optimisticLockingRetry.ejecutarConReintento(() ->
    {
      var actividad = actividadesRepository.findById(actividadId)
          .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));

      actividad.removerParticipante(usuario);
      actividadesRepository.save(actividad);
    });
  }

  @Override
  public PronosticoRespuestaDto obtenerClimaActividad(String actividadId, String usuarioId)
  {
    validarExistenciaUsuario(usuarioId);

    var actividad = actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));

    boolean esParticipante = actividad.getParticipantes().stream()
        .anyMatch(u -> u.getId().equals(usuarioId));
    boolean esOrganizador = actividad.getOrganizador() != null
        && actividad.getOrganizador().getId().equals(usuarioId);

    if (!esParticipante && !esOrganizador)
      throw new NoParticipanteException("Debes ser participante u organizador de la actividad para ver su clima");

    TipoProveedorClima proveedorPreferido = resolverProveedorPreferido(usuarioId, actividad);
    ContextoProveedorClima.iniciar(usuarioId, proveedorPreferido);
    try
    {
      ClimaDto climaActual = climaMapper.climaToClimaDto(proveedorClima.obtenerClima(actividad.getUbicacion()));
      ClimaDto pronostico = climaMapper.climaToClimaDto(
          proveedorClima.obtenerPronostico(actividad.getUbicacion(), actividad.getFechaRealizacion()));

      DatosContexto snapshot = ContextoProveedorClima.obtenerSnapshot();
      return new PronosticoRespuestaDto(
          climaActual,
          pronostico,
          snapshot.proveedorSeleccionado(),
          snapshot.proveedorUtilizado(),
          snapshot.fallbackActivo(),
          snapshot.degradado(),
          snapshot.mensajeEstado());
    } finally
    {
      ContextoProveedorClima.limpiar();
    }
  }

  private TipoProveedorClima resolverProveedorPreferido(String usuarioId, Actividad actividad)
  {
    var usuarioEnBase = usuarioRepository.findById(usuarioId);
    if (usuarioEnBase.isPresent())
      return usuarioEnBase.get().getProveedorClimaEfectivo();

    if (actividad.getOrganizador() != null && usuarioId.equals(actividad.getOrganizador().getId()))
      return actividad.getOrganizador().getProveedorClimaEfectivo();

    return actividad.getParticipantes().stream()
        .filter(u -> usuarioId.equals(u.getId()))
        .findFirst()
        .map(Usuario::getProveedorClimaEfectivo)
        .orElse(TipoProveedorClima.WEATHER_API);
  }

  /**
   * Cambia el estado de una actividad existente. Si el nuevo estado es CANCELADA,
   * delega en {@link #cancelarActividad(Actividad, String, String)} para garantizar
   * el orden de escrituras (cerrar votacion abierta primero, guardar actividad segundo
   * y notificar al final).
   *
   * @param actividadId Identificador de la actividad.
   * @param usuarioId   Identificador del usuario que solicita el cambio (debe ser el organizador).
   * @param nuevoEstado Nuevo estado de la actividad.
   */
  @Override
  public void cambiarEstado(String actividadId, String usuarioId, TipoEstadoActividad nuevoEstado)
  {
    var actividad = buscarActividad(actividadId);

    if (!actividad.getOrganizador().getId().equals(usuarioId))
      throw new AccesoDenegadoException(
          "Solo el organizador puede cambiar el estado de la actividad");

    if (nuevoEstado == TipoEstadoActividad.CANCELADA)
    {
      cancelarActividad(actividad, "cancelada por el organizador", null);
      return;
    }

    optimisticLockingRetry.ejecutarSobreInicialORecargar(
        actividad,
        () -> buscarActividad(actividadId),
        objetivo ->
        {
          objetivo.cambiarEstado(nuevoEstado);
          return actividadesRepository.save(objetivo);
        });
  }

  /**
   * Cancela una actividad respetando el orden de escrituras:
   * 1. Cierra condicionalmente la votacion abierta (si aun no fue cerrada).
   * 2. Cambia el estado de la actividad a CANCELADA y la persiste (con reintento ante conflicto optimista).
   * 3. Notifica a los participantes.
   *
   * @param actividad  Actividad a cancelar.
   * @param motivo     Motivo de la cancelacion para incluir en la notificacion.
   * @param votacionId Identificador de la votacion ya cerrada que origino la cancelacion, o null si aplica buscar/cerrar una abierta.
   */
  @Override
  public void cancelarActividad(Actividad actividad, String motivo, String votacionId)
  {
    if (actividad.getEstado() == null)
      throw new IllegalStateException(
          "La actividad id=" + actividad.getId() + " no tiene un estado configurado, no se puede cancelar");

    actividad.validarCambioEstado(TipoEstadoActividad.CANCELADA);

    if (votacionId == null)
      votacionesRepository.findByAbiertaTrueAndActividadId(actividad.getId())
          .ifPresent(votacion ->
          {
            votacion.cerrar(null);
            votacionesRepository.cerrarSiAbierta(votacion)
                .orElseThrow(() -> new VotacionCerradaException("La votacion ya esta cerrada"));
          });

    Actividad actividadCancelada = optimisticLockingRetry.ejecutarSobreInicialORecargar(
        actividad,
        () -> buscarActividad(actividad.getId()),
        objetivo ->
        {
          objetivo.cambiarEstado(TipoEstadoActividad.CANCELADA);
          Actividad guardada = actividadesRepository.save(objetivo);
          return guardada != null ? guardada : objetivo;
        });

    String contenido = "La actividad '%s' fue cancelada: %s.".formatted(actividadCancelada.getTitulo(), motivo);

    servicioNotificaciones.notificarATodos(contenido, actividadCancelada.getParticipantes());
    notificacionInboxService.crearParaTodos(contenido, TipoNotificacion.ACTIVIDAD_CANCELADA,
        actividadCancelada.getId(), votacionId, actividadCancelada.getParticipantes());
  }

  private Actividad buscarActividad(String actividadId)
  {
    return actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));
  }

  /**
   * Actualiza las configuraciones de clima, anticipacion y rango de reprogramacion de una actividad.
   *
   * @param actividadId Identificador de la actividad.
   * @param usuarioId   Identificador del usuario que solicita la configuracion (debe ser el organizador).
   * @param dto         DTO con las nuevas configuraciones a aplicar.
   * @return DTO de la actividad actualizada.
   */
  @Override
  public ActividadDto actualizarConfiguracionClima(String actividadId, String usuarioId, ConfigurarCondicionesDto dto)
  {
    Actividad actividad = actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));

    if (!actividad.getOrganizador().getId().equals(usuarioId))
      throw new AccesoDenegadoException("Solo el organizador puede configurar el clima");

    if (dto.reglasClima() != null)
      actividad.reemplazarReglasClima(
          dto.reglasClima().maxProbabilidadLluvia(),
          dto.reglasClima().minTemperatura(),
          dto.reglasClima().maxTemperatura(),
          dto.reglasClima().maxViento()
      );

    if (dto.horasAnticipacion() != null)
    {
      int maxHorasAnticipacion = maxDiasForecast * 24;
      if (dto.horasAnticipacion() > maxHorasAnticipacion)
        throw new IllegalArgumentException(
            "horasAnticipacion no puede superar %d horas (%d dias): limitacion del plan de WeatherAPI contratado"
                .formatted(maxHorasAnticipacion, maxDiasForecast));

      actividad.actualizarHorasAnticipacion(dto.horasAnticipacion());
    }

    if (dto.rangoReprogramacion() != null)
    {
      if (dto.rangoReprogramacion().dias() != null && dto.rangoReprogramacion().dias() > maxDiasForecast)
        throw new RangoReprogramacionInvalidoException(
            "El rango de reprogramacion no puede superar %d dias: limitacion del plan de WeatherAPI contratado"
                .formatted(maxDiasForecast),
            "diasReprogramacion");

      if (dto.rangoReprogramacion().horaInicio() != null && dto.rangoReprogramacion().horaFinal() != null)
      {
        int ventanaHoras = dto.rangoReprogramacion().horaFinal() - dto.rangoReprogramacion().horaInicio();
        if (actividad.getDuracionEstimada() > 0 && ventanaHoras < actividad.getDuracionEstimada())
          throw new RangoReprogramacionInvalidoException(
              "La franja horaria permitida (%d hs) no puede ser menor a la duracion estimada de la actividad (%d hs)"
                  .formatted(ventanaHoras, actividad.getDuracionEstimada()),
              "horaFinal");
      }

      actividad.actualizarRangoReprogramacion(
          dto.rangoReprogramacion().dias(),
          dto.rangoReprogramacion().horaInicio(),
          dto.rangoReprogramacion().horaFinal()
      );
    }

    actividadesRepository.save(actividad);

    return actividadesMapper.actividadToActividadDto(actividad);
  }
}
