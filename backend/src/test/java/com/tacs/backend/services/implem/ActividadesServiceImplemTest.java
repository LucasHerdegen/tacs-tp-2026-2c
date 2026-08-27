package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.EstadoActividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ActividadesServiceImplemTest
{

  @Mock
  private ActividadesRepository actividadesRepository;

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private ActividadesMapper actividadesMapper;

  @InjectMocks
  private ActividadesServiceImplem actividadesService;

  private ActividadPostDto actividadPostDto;
  private Usuario usuarioMock;
  private Actividad actividadMock;

  @BeforeEach
  void setUp()
  {
    Ubicacion ubicacion = new Ubicacion("Palermo", -34.588, -58.430);
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
    usuarioMock.setId(1L);

    actividadMock = new Actividad();
    actividadMock.setId(100L);
  }

  @Test
  @DisplayName("Crear actividad exitosamente - Debería retornar ActividadDto")
  void createActividad_Success_ReturnsActividadDto()
  {
    // Arrange
    ActividadDto expectedDto = mock(ActividadDto.class);

    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));
    when(actividadesMapper.actividadPostDtoToActividad(actividadPostDto, usuarioMock))
        .thenReturn(actividadMock);
    when(actividadesRepository.save(actividadMock)).thenReturn(actividadMock);
    when(actividadesMapper.actividadToActividadDto(actividadMock)).thenReturn(expectedDto);

    // Act
    ActividadDto result = actividadesService.createActividad(actividadPostDto);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedDto);
    assertThat(actividadMock.getEstado()).isEqualTo(TipoEstadoActividad.PROPUESTA);

    verify(usuarioRepository).findById(1L);
    verify(actividadesRepository).save(actividadMock);
  }

  @Test
  @DisplayName("Crear actividad falla por cantidad minima mayor a maxima - Lanza IllegalArgumentException")
  void createActividad_MinParticipantsGreaterThanMax_ThrowsException()
  {
    // Arrange
    ActividadPostDto dtoInvalido = new ActividadPostDto(
        "Partido 5v5", "Fútbol", TipoActividad.AIRE_LIBRE, new Ubicacion(),
        LocalDateTime.now().plusDays(2), 2,
        10, // cantidadMinima
        5   // cantidadMaxima (menor a la mínima)
    );

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.createActividad(dtoInvalido))
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
    when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.createActividad(actividadPostDto))
        .isInstanceOf(UsuarioNotFoundException.class)
        .hasMessageContaining("El usuario con id 1 no fue encontrado");

    verifyNoInteractions(actividadesRepository);
  }



  @Test
  @DisplayName("Cancelar actividad exitosamente - Cambia estado a CANCELADA")
  void cancelarActividad_Success_ChangesStateToCancelada()
  {
    // Arrange
    Long actividadId = 100L;
    Long organizadorId = 1L;
    
    actividadMock.setOrganizador(usuarioMock); // id 1L
    actividadMock.setEstado(TipoEstadoActividad.PROPUESTA);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act
    actividadesService.cancelarActividad(actividadId, organizadorId);

    // Assert
    assertThat(actividadMock.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(actividadesRepository).save(actividadMock);
  }

  @Test
  @DisplayName("Cancelar actividad por usuario que no es organizador - Lanza AccesoDenegadoException")
  void cancelarActividad_NotOrganizer_ThrowsAccesoDenegadoException()
  {
    // Arrange
    Long actividadId = 100L;
    Long intrusoId = 999L;
    
    actividadMock.setOrganizador(usuarioMock); // el organizador es 1L

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.cancelarActividad(actividadId, intrusoId))
        .isInstanceOf(com.tacs.backend.exceptions.AccesoDenegadoException.class)
        .hasMessage("Solo el organizador puede cancelar la actividad");

    verify(actividadesRepository, never()).save(any());
  }

  @Test
  @DisplayName("Cancelar actividad que no existe - Lanza ActividadNotFoundException")
  void cancelarActividad_ActividadNotFound_ThrowsActividadNotFoundException()
  {
    // Arrange
    Long actividadId = 999L;
    Long organizadorId = 1L;
    
    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.cancelarActividad(actividadId, organizadorId))
        .isInstanceOf(com.tacs.backend.exceptions.ActividadNotFoundException.class)
        .hasMessage("Actividad no encontrada");
  }

  @Test
  @DisplayName("Actualizar reglas de clima exitosamente")
  void actualizarConfiguracionClima_ReglasClima_Success()
  {
    // Arrange
    Long actividadId = 100L;
    Long organizadorId = 1L;
    actividadMock.setOrganizador(usuarioMock);

    var reglasDto = new com.tacs.backend.dtos.clima.ReglasClimaDto(30.0, 15.0, 28.0, 20.0);
    var configDto = new com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto(reglasDto, null, null);
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
  @DisplayName("Actualizar rango de reprogramación exitosamente")
  void actualizarConfiguracionClima_RangoReprogramacion_Success()
  {
    // Arrange
    Long actividadId = 100L;
    Long organizadorId = 1L;
    actividadMock.setOrganizador(usuarioMock);

    var rangoDto = new com.tacs.backend.dtos.actividades.RangoReprogramacionDto(3, -10, 20);
    var configDto = new com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto(null, null, rangoDto);
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
    Long actividadId = 100L;
    Long usuarioIntrusoId = 999L;
    actividadMock.setOrganizador(usuarioMock); // Organizador es 1L

    var configDto = new com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto(null, 12, null);
    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.actualizarConfiguracionClima(actividadId, usuarioIntrusoId, configDto))
        .isInstanceOf(com.tacs.backend.exceptions.AccesoDenegadoException.class)
        .hasMessageContaining("Solo el organizador");

    verify(actividadesRepository, never()).save(any());
  }

  @Test
  @DisplayName("Falla si la hora final de reprogramación es menor a la de inicio")
  void actualizarConfiguracionClima_RangoHorarioInvalido_ThrowsException()
  {
    // Arrange
    Long actividadId = 100L;
    Long organizadorId = 1L;
    actividadMock.setOrganizador(usuarioMock);

    // Hora inicio 20hs, Hora final 10hs (invalido)
    var rangoInvalidoDto = new com.tacs.backend.dtos.actividades.RangoReprogramacionDto(3, 20, 10);
    var configDto = new com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto(null, null, rangoInvalidoDto);

    when(actividadesRepository.findById(actividadId)).thenReturn(Optional.of(actividadMock));

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.actualizarConfiguracionClima(actividadId, organizadorId, configDto))
        .isInstanceOf(RangoReprogramacionInvalidoException.class);

    verify(actividadesRepository, never()).save(any());
  }
}
