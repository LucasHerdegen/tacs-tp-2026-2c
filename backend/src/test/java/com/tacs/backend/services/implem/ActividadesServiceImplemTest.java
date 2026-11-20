package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.dtos.actividades.RangoReprogramacionDto;
import com.tacs.backend.dtos.actividades.UbicacionDto;
import com.tacs.backend.dtos.clima.ClimaDto;
import com.tacs.backend.dtos.clima.PronosticoRespuestaDto;
import com.tacs.backend.dtos.clima.ReglasClimaDto;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.EstadoInvalidoException;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;
import com.tacs.backend.exceptions.RateLimitClimaExcedidoException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.VotacionCerradaException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.mappers.ClimaMapper;
import com.tacs.backend.observabilidad.ObservabilidadBusquedas;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.NotificacionInboxService;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActividadesServiceImplemTest
{

  @Mock
  private ActividadesRepository actividadesRepository;

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private ActividadesMapper actividadesMapper;

  @Mock
  private ServicioNotificaciones servicioNotificaciones;

  @Mock
  private ProveedorClima proveedorClima;

  @Mock
  private ClimaMapper climaMapper;

  @Mock
  private NotificacionInboxService notificacionInboxService;

  @Mock
  private VotacionesRepository votacionesRepository;

  @Spy
  private OptimisticLockingRetry optimisticLockingRetry = new OptimisticLockingRetry();

  @Spy
  private ObservabilidadBusquedas observabilidadBusquedas = new ObservabilidadBusquedas(OpenTelemetry.noop());

  @InjectMocks
  private ActividadesServiceImplem actividadesService;

  private ActividadPostDto actividadPostDto;
  private Usuario usuarioMock;
  private Actividad actividadMock;

  @BeforeEach
  void setUp()
  {
    UbicacionDto ubicacion = new UbicacionDto("Palermo", -34.588, -58.430);
    actividadPostDto = new ActividadPostDto(
        "Partido 5v5",
        "Fútbol en las canchas de Salguero",
        TipoActividad.AIRE_LIBRE,
        ubicacion,
        LocalDateTime.now().plusDays(2),
        2, // duracionEstimada
        10, // cantidadMinima
        10 // cantidadMaxima
    );

    usuarioMock = new Usuario();
    usuarioMock.setId("1");

    actividadMock = new Actividad();
    actividadMock.setId("100");
    actividadMock.setMaximoParticipantes(10);

    // maxDiasForecast se inyecta via @Value en produccion; en el test se
    // setea a mano con el mismo valor que application.properties.
    ReflectionTestUtils.setField(actividadesService, "maxDiasForecast", 3);
  }

  @Test
  @DisplayName("Crear actividad exitosamente - Debería retornar ActividadDto")
  void createActividad_Success_ReturnsActividadDto()
  {
    // Arrange
    ActividadDto expectedDto = mock(ActividadDto.class);

    when(usuarioRepository.findById("1")).thenReturn(Optional.of(usuarioMock));
    when(actividadesMapper.actividadPostDtoToActividad(actividadPostDto, usuarioMock))
        .thenReturn(actividadMock);
    when(actividadesRepository.save(actividadMock)).thenReturn(actividadMock);
    when(actividadesMapper.actividadToActividadDto(actividadMock)).thenReturn(expectedDto);

    // Act
    ActividadDto result = actividadesService.createActividad(actividadPostDto, "1");

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedDto);
    assertThat(actividadMock.getEstado()).isEqualTo(TipoEstadoActividad.PROPUESTA);

    verify(usuarioRepository).findById("1");
    verify(actividadesRepository).save(actividadMock);
  }

  @Test
  @DisplayName("Crear actividad falla por cantidad minima mayor a maxima - Lanza IllegalArgumentException")
  void createActividad_MinParticipantsGreaterThanMax_ThrowsException()
  {
    // Arrange
    ActividadPostDto dtoInvalido = new ActividadPostDto(
        "Partido 5v5", "Fútbol", TipoActividad.AIRE_LIBRE, new UbicacionDto(),
        LocalDateTime.now().plusDays(2), 2,
        10, // cantidadMinima
        5   // cantidadMaxima (menor a la mínima)
    );

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.createActividad(dtoInvalido, "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("La cantidad mínima no puede ser mayor a la máxima");

    // Verificamos que no se haya llamado a ningún repositorio
    verifyNoInteractions(usuarioRepository, actividadesRepository);
  }

  @Test
  @DisplayName("Crear actividad falla si no se encuentra el organizador - Lanza UsuarioNotFoundException")
  void createActividad_UsuarioNoEncontrado_ThrowsException()
  {
    // Arrange
    when(usuarioRepository.findById("1")).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.createActividad(actividadPostDto, "1"))
        .isInstanceOf(UsuarioNotFoundException.class)
        .hasMessageContaining("El usuario con id 1 no fue encontrado");

    verifyNoInteractions(actividadesRepository);
  }


  @Test
  @DisplayName("Cancelar actividad exitosamente - Cambia estado a CANCELADA")
  void cancelarActividad_Success_ChangesStateToCancelada()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";

    actividadMock.setOrganizador(usuarioMock); // id "1"
    actividadMock.setEstado(TipoEstadoActividad.PROPUESTA);
    actividadMock.setFechaRealizacion(LocalDateTime.now().plusDays(1));

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act
    actividadesService.cambiarEstado(actividadId, organizadorId,
        TipoEstadoActividad.CANCELADA);

    // Assert
    assertThat(actividadMock.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(actividadesRepository).save(actividadMock);
  }

  @Test
  @DisplayName("Cancelar actividad por usuario que no es organizador - Lanza AccesoDenegadoException")
  void cancelarActividad_NotOrganizer_ThrowsAccesoDenegadoException()
  {
    // Arrange
    String actividadId = "100";
    String intrusoId = "999";

    actividadMock.setOrganizador(usuarioMock); // el organizador es "1"

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.cambiarEstado(actividadId, intrusoId,
        TipoEstadoActividad.CANCELADA))
        .isInstanceOf(AccesoDenegadoException.class)
        .hasMessage("Solo el organizador puede cambiar el estado de la actividad");

    verify(actividadesRepository, never()).save(any());
  }

  @Test
  @DisplayName("Cancelar actividad que no existe - Lanza ActividadNotFoundException")
  void cancelarActividad_ActividadNotFound_ThrowsActividadNotFoundException()
  {
    // Arrange
    String actividadId = "999";
    String organizadorId = "1";

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.cambiarEstado(actividadId, organizadorId,
        TipoEstadoActividad.CANCELADA))
        .isInstanceOf(ActividadNotFoundException.class)
        .hasMessage("Actividad no encontrada");
  }

  @Test
  @DisplayName("Cancelar actividad con votacion abierta - Cierra primero la votacion condicionalmente, luego guarda actividad y por ultimo notifica")
  void cancelarActividad_ConVotacionAbierta_CierraVotacionPrimeroLuegoGuardaYNotifica()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setId(actividadId);
    actividadMock.setTitulo("Partido 5v5");
    actividadMock.setOrganizador(usuarioMock);
    actividadMock.setEstado(TipoEstadoActividad.PROPUESTA);
    actividadMock.setFechaRealizacion(LocalDateTime.now().plusDays(1));

    Votacion votacionMock = new Votacion();
    votacionMock.setId("vot-1");
    votacionMock.setAbierta(true);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(actividadId)).thenReturn(Optional.of(votacionMock));
    when(votacionesRepository.cerrarSiAbierta(votacionMock)).thenReturn(Optional.of(votacionMock));

    // Act
    actividadesService.cambiarEstado(actividadId, organizadorId, TipoEstadoActividad.CANCELADA);

    // Assert
    assertThat(actividadMock.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    assertThat(votacionMock.isAbierta()).isFalse();

    InOrder orden = inOrder(votacionesRepository, actividadesRepository, servicioNotificaciones, notificacionInboxService);
    orden.verify(votacionesRepository).cerrarSiAbierta(votacionMock);
    orden.verify(actividadesRepository).save(actividadMock);
    orden.verify(servicioNotificaciones).notificarATodos(any(), any());
    orden.verify(notificacionInboxService).crearParaTodos(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Cancelar actividad falla al cerrar votacion ya cerrada - No guarda actividad ni notifica")
  void cancelarActividad_CierreCondicionalFalla_NoGuardaActividadNiNotifica()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setId(actividadId);
    actividadMock.setTitulo("Partido 5v5");
    actividadMock.setOrganizador(usuarioMock);
    actividadMock.setEstado(TipoEstadoActividad.PROPUESTA);

    Votacion votacionMock = new Votacion();
    votacionMock.setId("vot-1");
    votacionMock.setAbierta(true);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(actividadId)).thenReturn(Optional.of(votacionMock));
    when(votacionesRepository.cerrarSiAbierta(votacionMock)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.cambiarEstado(actividadId, organizadorId, TipoEstadoActividad.CANCELADA))
        .isInstanceOf(VotacionCerradaException.class);

    assertThat(actividadMock.getEstado()).isEqualTo(TipoEstadoActividad.PROPUESTA);
    verify(actividadesRepository, never()).save(any());
    verifyNoInteractions(servicioNotificaciones, notificacionInboxService);
  }

  @Test
  @DisplayName("Cancelar actividad con conflicto optimista reintenta recargando la actividad y notifica a los participantes actualizados")
  void cancelarActividad_ConflictoOptimistaAlGuardar_ReintentaYNotificaATodos()
  {
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setId(actividadId);
    actividadMock.setTitulo("Partido 5v5");
    actividadMock.setOrganizador(usuarioMock);
    actividadMock.setEstado(TipoEstadoActividad.PROPUESTA);

    Actividad recargada = new Actividad();
    recargada.setId(actividadId);
    recargada.setTitulo("Partido 5v5");
    recargada.setOrganizador(usuarioMock);
    recargada.setEstado(TipoEstadoActividad.CONFIRMADA);
    Usuario nuevo = new Usuario("nuevo", "pass", TipoRol.USER);
    nuevo.setId("2");
    recargada.setParticipantes(List.of(usuarioMock, nuevo));

    when(actividadesRepository.findById(actividadId))
        .thenReturn(Optional.of(actividadMock))
        .thenReturn(Optional.of(recargada));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(actividadId)).thenReturn(Optional.empty());
    when(actividadesRepository.save(any(Actividad.class)))
        .thenThrow(new OptimisticLockingFailureException("Conflicto optimista simulado"))
        .thenAnswer(inv -> inv.getArgument(0));

    actividadesService.cambiarEstado(actividadId, organizadorId, TipoEstadoActividad.CANCELADA);

    assertThat(recargada.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(actividadesRepository, times(2)).save(any(Actividad.class));
    verify(servicioNotificaciones, times(1)).notificarATodos(any(), eq(recargada.getParticipantes()));
  }

  @Test
  @DisplayName("Cancelar actividad FINALIZADA lanza EstadoInvalidoException de transicion sin mencionar votacion")
  void cancelarActividad_ActividadFinalizada_LanzaEstadoInvalidoDeTransicion()
  {
    String actividadId = "100";
    actividadMock.setId(actividadId);
    actividadMock.setOrganizador(usuarioMock);
    actividadMock.setEstado(TipoEstadoActividad.FINALIZADA);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    assertThatThrownBy(() -> actividadesService.cambiarEstado(actividadId, "1", TipoEstadoActividad.CANCELADA))
        .isInstanceOf(EstadoInvalidoException.class)
        .hasMessage("No se puede pasar de FINALIZADA a CANCELADA");

    verify(votacionesRepository, never()).findByAbiertaTrueAndActividadId(any());
    verify(actividadesRepository, never()).save(any());
  }

  @Test
  @DisplayName("Unirse a actividad cancelada - Lanza EstadoInvalidoException")
  void unirseActividad_ActividadCancelada_ThrowsEstadoInvalidoException()
  {
    // Arrange
    String actividadId = "100";
    String nuevoUsuarioId = "2";
    Usuario nuevoUsuario = new Usuario("usuario2", "password", TipoRol.USER);
    nuevoUsuario.setId(nuevoUsuarioId);

    actividadMock.setId(actividadId);
    actividadMock.setEstado(TipoEstadoActividad.CANCELADA);

    when(usuarioRepository.existsById(nuevoUsuarioId)).thenReturn(true);
    when(usuarioRepository.findById(nuevoUsuarioId)).thenReturn(Optional.of(nuevoUsuario));
    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.unirseActividad(actividadId, nuevoUsuarioId))
        .isInstanceOf(EstadoInvalidoException.class);
  }

  @Test
  @DisplayName("Actualizar reglas de clima exitosamente")
  void actualizarConfiguracionClima_ReglasClima_Success()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setOrganizador(usuarioMock);

    var reglasDto = new ReglasClimaDto(30.0, 15.0, 28.0, 20.0);
    var configDto = new ConfigurarCondicionesDto(reglasDto, null, null);
    ActividadDto expectedDto = mock(ActividadDto.class);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(actividadesRepository.save(actividadMock)).thenReturn(actividadMock);
    when(actividadesMapper.actividadToActividadDto(actividadMock)).thenReturn(expectedDto);

    // Act
    ActividadDto result = actividadesService.actualizarConfiguracionClima(actividadId, organizadorId, configDto);

    // Assert (Una sola aserción agrupada)
    assertThat(result).isEqualTo(expectedDto);
    assertThat(actividadMock.getReglasClima()).satisfies(reglas -> {
      assertThat(reglas.getMaxProbabilidadLluvia()).isEqualTo(30.0);
      assertThat(reglas.getMinTemperatura()).isEqualTo(15.0);
    });

    verify(actividadesRepository).save(actividadMock);
  }

  @Test
  @DisplayName("Actualizar reglas de clima con maxTemperatura = 0 y maxViento = 0 preserva el cero sin convertirlo en null")
  void actualizarConfiguracionClima_MaxTempYMaxVientoEnCero_PreservaValoresCero()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setOrganizador(usuarioMock);

    var reglasDto = new ReglasClimaDto(10.0, -10.0, 0.0, 0.0);
    var configDto = new ConfigurarCondicionesDto(reglasDto, null, null);
    ActividadDto expectedDto = mock(ActividadDto.class);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(actividadesRepository.save(actividadMock)).thenReturn(actividadMock);
    when(actividadesMapper.actividadToActividadDto(actividadMock)).thenReturn(expectedDto);

    // Act
    actividadesService.actualizarConfiguracionClima(actividadId, organizadorId, configDto);

    // Assert
    assertThat(actividadMock.getReglasClima()).satisfies(reglas -> {
      assertThat(reglas.getMaxTemperatura()).isEqualTo(0.0);
      assertThat(reglas.getMaxViento()).isEqualTo(0.0);
    });
  }

  @Test
  @DisplayName("Actualizar rango de reprogramación exitosamente")
  void actualizarConfiguracionClima_RangoReprogramacion_Success()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setOrganizador(usuarioMock);

    var rangoDto = new RangoReprogramacionDto(3, -10, 20);
    var configDto = new ConfigurarCondicionesDto(null, null, rangoDto);
    ActividadDto expectedDto = mock(ActividadDto.class);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(actividadesRepository.save(actividadMock)).thenReturn(actividadMock);
    when(actividadesMapper.actividadToActividadDto(actividadMock)).thenReturn(expectedDto);

    // Act & Assert
    ActividadDto result = actividadesService.actualizarConfiguracionClima(actividadId, organizadorId, configDto);

    assertThat(result).isEqualTo(expectedDto);
    assertThat(actividadMock.getRangoReprogramacion().getDias()).isEqualTo(3);
  }

  @Test
  @DisplayName("Falla si un usuario distinto al organizador intenta modificar la configuración")
  void actualizarConfiguracionClima_NotOrganizer_ThrowsAccesoDenegadoException()
  {
    // Arrange
    String actividadId = "100";
    String usuarioIntrusoId = "999";
    actividadMock.setOrganizador(usuarioMock); // Organizador es "1"

    var configDto = new ConfigurarCondicionesDto(null, 12, null);
    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.actualizarConfiguracionClima(actividadId, usuarioIntrusoId, configDto))
        .isInstanceOf(AccesoDenegadoException.class)
        .hasMessageContaining("Solo el organizador");

    verify(actividadesRepository, never()).save(any());
  }

  @Test
  @DisplayName("Falla si la hora final de reprogramación es menor a la de inicio")
  void actualizarConfiguracionClima_RangoHorarioInvalido_ThrowsException()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setOrganizador(usuarioMock);

    // Hora inicio 20hs, Hora final 10hs (invalido)
    var rangoInvalidoDto = new RangoReprogramacionDto(3, 20, 10);
    var configDto = new ConfigurarCondicionesDto(null, null, rangoInvalidoDto);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.actualizarConfiguracionClima(actividadId, organizadorId, configDto))
        .isInstanceOf(RangoReprogramacionInvalidoException.class);

    verify(actividadesRepository, never()).save(any());
  }

  @Test
  @DisplayName("Falla si la franja horaria permitida es menor a la duracion estimada de la actividad")
  void actualizarConfiguracionClima_FranjaHorariaMenorQueDuracionEstimada_ThrowsException()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setOrganizador(usuarioMock);
    actividadMock.setDuracionEstimada(4);

    // Ventana permitida: de 10 a 12 (2 hs), pero la duracion estimada es 4 hs
    var rangoInvalidoDto = new RangoReprogramacionDto(3, 10, 12);
    var configDto = new ConfigurarCondicionesDto(null, null, rangoInvalidoDto);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.actualizarConfiguracionClima(actividadId, organizadorId, configDto))
        .isInstanceOf(RangoReprogramacionInvalidoException.class)
        .hasMessageContaining("duracion estimada");

    verify(actividadesRepository, never()).save(any());
  }

  @Test
  @DisplayName("Actualiza correctamente si la franja horaria permitida cubre la duracion estimada de la actividad")
  void actualizarConfiguracionClima_FranjaHorariaValidaConDuracionEstimada_ActualizaCorrectamente()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setOrganizador(usuarioMock);
    actividadMock.setDuracionEstimada(2);

    var rangoValidoDto = new RangoReprogramacionDto(3, 10, 14); // ventana de 4 hs >= 2 hs
    var configDto = new ConfigurarCondicionesDto(null, 24, rangoValidoDto);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(actividadesRepository.save(actividadMock)).thenReturn(actividadMock);

    // Act
    actividadesService.actualizarConfiguracionClima(actividadId, organizadorId, configDto);

    // Assert
    verify(actividadesRepository).save(actividadMock);
    assertThat(actividadMock.getRangoReprogramacion().getHoraInicio()).isEqualTo(10);
    assertThat(actividadMock.getRangoReprogramacion().getHoraFinal()).isEqualTo(14);
  }

  @Test
  @DisplayName("Falla si horasAnticipacion supera el tope del plan de WeatherAPI (3 dias = 72hs)")
  void actualizarConfiguracionClima_HorasAnticipacionSuperaElTope_ThrowsException()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setOrganizador(usuarioMock);

    var configDto = new ConfigurarCondicionesDto(null, 73, null); // 3 dias * 24hs + 1

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.actualizarConfiguracionClima(actividadId, organizadorId, configDto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("WeatherAPI");

    verify(actividadesRepository, never()).save(any());
  }

  @Test
  @DisplayName("Falla si RangoReprogramacion.dias supera el tope del plan de WeatherAPI (3 dias)")
  void actualizarConfiguracionClima_RangoReprogramacionDiasSuperaElTope_ThrowsException()
  {
    // Arrange
    String actividadId = "100";
    String organizadorId = "1";
    actividadMock.setOrganizador(usuarioMock);

    var rangoDto = new RangoReprogramacionDto(4, 10, 20); // supera el tope de 3 dias
    var configDto = new ConfigurarCondicionesDto(null, null, rangoDto);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.actualizarConfiguracionClima(actividadId, organizadorId, configDto))
        .isInstanceOf(RangoReprogramacionInvalidoException.class)
        .hasMessageContaining("WeatherAPI");

    verify(actividadesRepository, never()).save(any());
  }

  @Test
  @DisplayName("Obtener clima de actividad con ambos proveedores indisponibles - Devuelve respuesta degradada explicita sin lanzar 503")
  void obtenerClimaActividad_ProveedorIndisponible_DevuelveRespuestaDegradada()
  {
    // Arrange
    String actividadId = "100";
    String usuarioId = "1";
    actividadMock.setParticipantes(List.of(usuarioMock));

    when(usuarioRepository.existsById(usuarioId)).thenReturn(true);
    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(proveedorClima.obtenerClima(any()))
        .thenThrow(new ProveedorClimaIndisponibleException("Proveedor de clima no disponible"));
    when(proveedorClima.obtenerPronostico(any(), any()))
        .thenThrow(new ProveedorClimaIndisponibleException("Proveedor de clima no disponible"));

    // Act
    PronosticoRespuestaDto respuesta = actividadesService.obtenerClimaActividad(actividadId, usuarioId);

    // Assert: US3 degradacion explicita en lugar de propagar 503
    assertThat(respuesta.degradado()).isTrue();
    assertThat(respuesta.climaActual()).isNull();
    assertThat(respuesta.pronosticoFuturo()).isNull();
    assertThat(respuesta.proveedorUtilizado()).isNull();
    assertThat(respuesta.mensajeEstado()).containsIgnoringCase("modo degradado");
  }

  @Test
  @DisplayName("Obtener clima de actividad con rechazo por rate limit en clima actual pero pronostico disponible - Devuelve degradacion parcial")
  void obtenerClimaActividad_RateLimitEnClimaActualYPronosticoDisponible_DevuelveDegradacionParcial()
  {
    String actividadId = "100";
    String usuarioId = "1";
    actividadMock.setParticipantes(List.of(usuarioMock));
    Clima pronosticoDominio = new Clima(15.0, 22.0, 10.0);
    ClimaDto pronosticoDto = new ClimaDto(15.0, 22.0, 10.0);

    when(usuarioRepository.existsById(usuarioId)).thenReturn(true);
    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(proveedorClima.obtenerClima(any()))
        .thenThrow(new RateLimitClimaExcedidoException("Rate limit excedido hacia WeatherAPI", null));
    when(proveedorClima.obtenerPronostico(any(), any())).thenReturn(pronosticoDominio);
    when(climaMapper.climaToClimaDto(pronosticoDominio)).thenReturn(pronosticoDto);

    PronosticoRespuestaDto respuesta = actividadesService.obtenerClimaActividad(actividadId, usuarioId);

    assertThat(respuesta.degradado()).isTrue();
    assertThat(respuesta.climaActual()).isNull();
    assertThat(respuesta.pronosticoFuturo()).isEqualTo(pronosticoDto);
    assertThat(respuesta.mensajeEstado()).containsIgnoringCase("alta demanda");
  }

  @Test
  @DisplayName("Obtener clima de actividad con rechazo por rate limit en ambas consultas - Devuelve degradacion explicita por alta demanda")
  void obtenerClimaActividad_RateLimitEnAmbasConsultas_DevuelveDegradacionPorAltaDemanda()
  {
    String actividadId = "100";
    String usuarioId = "1";
    actividadMock.setParticipantes(List.of(usuarioMock));

    when(usuarioRepository.existsById(usuarioId)).thenReturn(true);
    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(proveedorClima.obtenerClima(any()))
        .thenThrow(new RateLimitClimaExcedidoException("Rate limit excedido hacia WeatherAPI", null));
    when(proveedorClima.obtenerPronostico(any(), any()))
        .thenThrow(new RateLimitClimaExcedidoException("Rate limit excedido hacia WeatherAPI", null));

    PronosticoRespuestaDto respuesta = actividadesService.obtenerClimaActividad(actividadId, usuarioId);

    assertThat(respuesta.degradado()).isTrue();
    assertThat(respuesta.climaActual()).isNull();
    assertThat(respuesta.pronosticoFuturo()).isNull();
    assertThat(respuesta.proveedorUtilizado()).isNull();
    assertThat(respuesta.mensajeEstado()).containsIgnoringCase("alta demanda");
  }

  @Test
  @DisplayName("Obtener clima de actividad siendo organizador pero no participante - Exitoso")
  void obtenerClimaActividad_OrganizadorNoParticipante_RetornaPronostico()
  {
    // Arrange
    String actividadId = "100";
    String usuarioId = "1";
    actividadMock.setOrganizador(usuarioMock);
    actividadMock.setParticipantes(List.of());

    when(usuarioRepository.existsById(usuarioId)).thenReturn(true);
    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));
    when(proveedorClima.obtenerClima(any())).thenReturn(mock(Clima.class));
    when(proveedorClima.obtenerPronostico(any(), any())).thenReturn(mock(Clima.class));

    // Act
    var pronostico = actividadesService.obtenerClimaActividad(actividadId, usuarioId);

    // Assert
    assertThat(pronostico).isNotNull();
  }

  @Test
  @DisplayName("Obtener clima de actividad no siendo ni participante ni organizador - Lanza NoParticipanteException")
  void obtenerClimaActividad_NoParticipanteNiOrganizador_LanzaNoParticipanteException()
  {
    // Arrange
    String actividadId = "100";
    String usuarioId = "99";
    actividadMock.setOrganizador(usuarioMock);
    actividadMock.setParticipantes(List.of());

    when(usuarioRepository.existsById(usuarioId)).thenReturn(true);
    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.obtenerClimaActividad(actividadId, usuarioId))
        .isInstanceOf(NoParticipanteException.class);
  }

  @Test
  @DisplayName("Buscar actividades delega al repositorio y mapea a DTO exitosamente (Happy Path)")
  void buscarActividades_ParametrosValidos_DelegaAlRepositorioYRetornaPageDto()
  {
    // Arrange
    TipoActividad tipo = TipoActividad.AIRE_LIBRE;
    String busqueda = "parque";
    LocalDate fechaInicio = LocalDate.of(2026, 11, 1);
    LocalDate fechaFin = LocalDate.of(2026, 11, 5);
    TipoEstadoActividad estado = TipoEstadoActividad.CONFIRMADA;
    Boolean cupoDisponible = true;
    Pageable pageable = PageRequest.of(0, 10);

    ActividadDto dtoMock = mock(ActividadDto.class);
    Page<Actividad> pageMock = new PageImpl<>(List.of(actividadMock), pageable, 1);

    when(actividadesRepository.buscarActividades(tipo, busqueda, fechaInicio, fechaFin, estado, cupoDisponible, pageable))
        .thenReturn(pageMock);
    when(actividadesMapper.actividadToActividadDto(actividadMock)).thenReturn(dtoMock);

    // Act
    Page<ActividadDto> resultado = actividadesService.buscarActividades(
        tipo, busqueda, fechaInicio, fechaFin, estado, cupoDisponible, pageable);

    // Assert
    assertThat(resultado).isNotNull();
    assertThat(resultado.getTotalElements()).isEqualTo(1);
    assertThat(resultado.getContent()).containsExactly(dtoMock);

    verify(actividadesRepository).buscarActividades(tipo, busqueda, fechaInicio, fechaFin, estado, cupoDisponible, pageable);
    verify(actividadesRepository, never()).findAll();
  }

  @Test
  @DisplayName("Buscar actividades con fecha inicio posterior a fecha fin lanza IllegalArgumentException (Failing Path)")
  void buscarActividades_FechaInicioPosteriorAFechaFin_LanzaIllegalArgumentException()
  {
    // Arrange
    LocalDate fechaInicio = LocalDate.of(2026, 11, 10);
    LocalDate fechaFin = LocalDate.of(2026, 11, 5);
    Pageable pageable = PageRequest.of(0, 10);

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.buscarActividades(
        null, null, fechaInicio, fechaFin, null, null, pageable))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("La fecha de inicio no puede ser posterior a la fecha de fin");

    verifyNoInteractions(actividadesRepository);
  }

  @Test
  @DisplayName("Buscar actividades con sort por campo inexistente lanza IllegalArgumentException")
  void buscarActividades_SortConCampoInexistente_LanzaIllegalArgumentException()
  {
    Pageable pageable = PageRequest.of(0, 10, Sort.by("campoQueNoExiste"));

    assertThatThrownBy(() -> actividadesService.buscarActividades(
        null, null, null, null, null, null, pageable))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("campoQueNoExiste");

    verifyNoInteractions(actividadesRepository);
  }

  @Test
  @DisplayName("Buscar actividades con sort por campo valido del DTO delega al repositorio")
  void buscarActividades_SortConCampoValidoDto_DelegaAlRepositorio()
  {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "tipoActividad"));
    Page<Actividad> pageMock = new PageImpl<>(List.of(), pageable, 0);
    when(actividadesRepository.buscarActividades(null, null, null, null, null, null, pageable))
        .thenReturn(pageMock);

    Page<ActividadDto> resultado = actividadesService.buscarActividades(
        null, null, null, null, null, null, pageable);

    assertThat(resultado).isEmpty();
    verify(actividadesRepository).buscarActividades(null, null, null, null, null, null, pageable);
  }
}
