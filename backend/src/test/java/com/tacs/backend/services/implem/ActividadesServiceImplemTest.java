package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.EstadoActividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.EstadoActividadRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActividadesServiceImplemTest
{

  @Mock
  private ActividadesRepository actividadesRepository;

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private EstadoActividadRepository estadoActividadRepository;

  @Mock
  private ActividadesMapper actividadesMapper;

  @InjectMocks
  private ActividadesServiceImplem actividadesService;

  private ActividadPostDto actividadPostDto;
  private Usuario usuarioMock;
  private Actividad actividadMock;
  private EstadoActividad estadoPropuesta;

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

    estadoPropuesta = new EstadoActividad();
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
    when(estadoActividadRepository.findByTipo(TipoEstadoActividad.PROPUESTA))
        .thenReturn(Optional.of(estadoPropuesta));
    when(actividadesRepository.save(actividadMock)).thenReturn(actividadMock);
    when(actividadesMapper.actividadToActividadDto(actividadMock)).thenReturn(expectedDto);

    // Act
    ActividadDto result = actividadesService.createActividad(actividadPostDto);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedDto);

    verify(usuarioRepository).findById(1L);
    verify(estadoActividadRepository).findByTipo(TipoEstadoActividad.PROPUESTA);
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
    verifyNoInteractions(usuarioRepository, estadoActividadRepository, actividadesRepository);
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

    verifyNoInteractions(estadoActividadRepository, actividadesRepository);
  }

  @Test
  @DisplayName("Crear actividad falla si no existe el estado inicial - Lanza IllegalStateException")
  void createActividad_EstadoInicialNoEncontrado_ThrowsException()
  {
    // Arrange
    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));
    when(actividadesMapper.actividadPostDtoToActividad(actividadPostDto, usuarioMock))
        .thenReturn(actividadMock);
    when(estadoActividadRepository.findByTipo(TipoEstadoActividad.PROPUESTA))
        .thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> actividadesService.createActividad(actividadPostDto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Estado inicial PROPUESTA no configurado en la base de datos");

    verifyNoInteractions(actividadesRepository);
  }
}
