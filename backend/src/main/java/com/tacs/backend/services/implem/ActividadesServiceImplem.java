package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.clima.ClimaDto;
import com.tacs.backend.dtos.clima.PronosticoRespuestaDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.domain.notificacion.TipoNotificacion;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.NotificacionInboxService;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.tacs.backend.mappers.ClimaMapper;

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

  public ActividadesServiceImplem(
      ActividadesRepository actividadesRepository,
      UsuarioRepository usuarioRepository,
      ActividadesMapper actividadesMapper,
      ProveedorClima proveedorClima,
      ClimaMapper climaMapper,
      ServicioNotificaciones servicioNotificaciones,
      NotificacionInboxService notificacionInboxService)
  {
    this(actividadesRepository, usuarioRepository, actividadesMapper,
        proveedorClima, climaMapper, servicioNotificaciones, notificacionInboxService,
        null, new OptimisticLockingRetry());
  }

  public ActividadesServiceImplem(
      ActividadesRepository actividadesRepository,
      UsuarioRepository usuarioRepository,
      ActividadesMapper actividadesMapper,
      ProveedorClima proveedorClima,
      ClimaMapper climaMapper,
      ServicioNotificaciones servicioNotificaciones,
      NotificacionInboxService notificacionInboxService,
      OptimisticLockingRetry optimisticLockingRetry)
  {
    this(actividadesRepository, usuarioRepository, actividadesMapper,
        proveedorClima, climaMapper, servicioNotificaciones, notificacionInboxService,
        null, optimisticLockingRetry);
  }

  @Autowired
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
    this.actividadesRepository = actividadesRepository;
    this.usuarioRepository = usuarioRepository;
    this.actividadesMapper = actividadesMapper;
    this.proveedorClima = proveedorClima;
    this.climaMapper = climaMapper;
    this.servicioNotificaciones = servicioNotificaciones;
    this.notificacionInboxService = notificacionInboxService;
    this.votacionesRepository = votacionesRepository;
    this.optimisticLockingRetry = optimisticLockingRetry;
  }

  @Value("${weatherapi.forecast.max-days:3}")
  private int maxDiasForecast;

  private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd:MM:yyyy HH:mm");

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
  public Page<ActividadDto> buscarActividades(TipoActividad tipo, String busqueda,
                                              LocalDate fechaInicio, LocalDate fechaFin,
                                              TipoEstadoActividad estado,
                                              Boolean cupoDisponible,
                                              Pageable pageable)
  {
    if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin))
    {
      throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin");
    }

    Page<Actividad> actividades = actividadesRepository.buscarActividades(
        tipo, busqueda, fechaInicio, fechaFin, estado, cupoDisponible, pageable);

    return actividades.map(actividadesMapper::actividadToActividadDto);
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

    optimisticLockingRetry.ejecutarConReintento(() -> {
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

    optimisticLockingRetry.ejecutarConReintento(() -> {
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


    ClimaDto climaActual = climaMapper.climaToClimaDto(proveedorClima.obtenerClima(actividad.getUbicacion()));
    ClimaDto pronostico = climaMapper.climaToClimaDto(
        proveedorClima.obtenerPronostico(actividad.getUbicacion(), actividad.getFechaRealizacion()));

    return new PronosticoRespuestaDto(climaActual, pronostico);
  }

  /**
   * Cancela una actividad existente y notifica a los participantes.
   *
   * @param actividadId Identificador de la actividad a cancelar.
   * @param usuarioId   Identificador del usuario que solicita la cancelacion (debe ser el organizador).
   */
  @Override
  public void cambiarEstado(String actividadId, String usuarioId, TipoEstadoActividad nuevoEstado)
  {
    var actividad = actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));

    if (!actividad.getOrganizador().getId().equals(usuarioId))
      throw new AccesoDenegadoException(
          "Solo el organizador puede cambiar el estado de la actividad");

    actividad.cambiarEstado(nuevoEstado);

    actividadesRepository.save(actividad);

    if (nuevoEstado == TipoEstadoActividad.CANCELADA)
    {
      if (votacionesRepository != null)
      {
        votacionesRepository.findByAbiertaTrueAndActividadId(actividad.getId())
            .ifPresent(votacion -> {
              votacion.cerrar(null);
              votacionesRepository.save(votacion);
            });
      }

      String contenido = "La actividad '%s' del '%s' fue cancelada por el organizador".formatted(
          actividad.getTitulo(), actividad.getFechaRealizacion().format(FORMATO));

      servicioNotificaciones.notificarATodos(contenido, actividad.getParticipantes());
      notificacionInboxService.crearParaTodos(contenido, TipoNotificacion.ACTIVIDAD_CANCELADA,
          actividad.getId(), null, actividad.getParticipantes());
    }
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
    {
      Double maxTemp = dto.reglasClima().maxTemperatura();
      if (maxTemp != null && maxTemp == 0.0)
      {
        maxTemp = null;
      }
      Double maxViento = dto.reglasClima().maxViento();
      if (maxViento != null && maxViento == 0.0)
      {
        maxViento = null;
      }

      actividad.reemplazarReglasClima(
          dto.reglasClima().maxProbabilidadLluvia(),
          dto.reglasClima().minTemperatura(),
          maxTemp,
          maxViento
      );
    }

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
                .formatted(maxDiasForecast));

      if (dto.rangoReprogramacion().horaInicio() != null && dto.rangoReprogramacion().horaFinal() != null)
      {
        int ventanaHoras = dto.rangoReprogramacion().horaFinal() - dto.rangoReprogramacion().horaInicio();
        if (actividad.getDuracionEstimada() > 0 && ventanaHoras < actividad.getDuracionEstimada())
        {
          throw new RangoReprogramacionInvalidoException(
              "La franja horaria permitida (%d hs) no puede ser menor a la duracion estimada de la actividad (%d hs)"
                  .formatted(ventanaHoras, actividad.getDuracionEstimada()));
        }
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
