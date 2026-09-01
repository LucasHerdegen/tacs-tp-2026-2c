package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.mappers.ClimaMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre la notificacion de la cancelacion manual (el TODO de User Story 13 en
 * ActividadesServiceImplem.cancelarActividad).
 *
 * Va en una clase aparte de ActividadesServiceImplemTest para no chocar con
 * ediciones en paralelo sobre ese archivo; se puede fusionar despues.
 *
 * Usa @InjectMocks a proposito: inyecta por tipo, asi que no importa en que
 * posicion quede el campo ServicioNotificaciones en el constructor generado
 * por @RequiredArgsConstructor.
 */
@ExtendWith(MockitoExtension.class)
class ActividadesServiceImplemNotificacionesTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);
  private static final Long ORGANIZADOR_ID = 999L;

  @Mock
  private ActividadesRepository actividadesRepository;

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private ActividadesMapper actividadesMapper;

  @Mock
  private ProveedorClima proveedorClima;

  @Mock
  private ClimaMapper climaMapper;

  @Mock
  private ServicioNotificaciones servicioNotificaciones;

  @InjectMocks
  private ActividadesServiceImplem service;

  @Test
  void cancelarNotificaATodosLosParticipantes()
  {
    Actividad actividad = crearActividad();

    when(actividadesRepository.findById(1L)).thenReturn(Optional.of(actividad));

    service.cancelarActividad(1L, ORGANIZADOR_ID);

    verify(servicioNotificaciones)
        .notificarATodos(contains(actividad.getTitulo()), eq(actividad.getParticipantes()));
  }

  @Test
  void cancelarDejaLaActividadEnEstadoCancelada()
  {
    Actividad actividad = crearActividad();

    when(actividadesRepository.findById(1L)).thenReturn(Optional.of(actividad));

    service.cancelarActividad(1L, ORGANIZADOR_ID);

    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(actividadesRepository).save(actividad);
  }

  // Si el que pide la cancelacion no es el organizador, no se cancela nada:
  // tampoco tiene que salir una notificacion avisando de algo que no paso.
  @Test
  void noNotificaSiQuienCancelaNoEsElOrganizador()
  {
    Actividad actividad = crearActividad();

    when(actividadesRepository.findById(1L)).thenReturn(Optional.of(actividad));

    assertThatThrownBy(() -> service.cancelarActividad(1L, 123L))
        .isInstanceOf(AccesoDenegadoException.class);

    verify(servicioNotificaciones, never()).notificarATodos(anyString(), any());
  }

  /* ==================== Auxiliares ==================== */

  // PROPUESTA porque, segun Estados, es un estado desde el que se puede llegar a CANCELADA.
  private Actividad crearActividad()
  {
    Actividad actividad = new Actividad(
        "Asado en el parque",
        "Actividad de prueba",
        TipoActividad.AIRE_LIBRE,
        UBICACION,
        LocalDateTime.now().plusDays(1),
        2,
        LocalDateTime.now(),
        2,
        10,
        crearUsuarioConId(ORGANIZADOR_ID));

    actividad.setEstado(TipoEstadoActividad.PROPUESTA);
    actividad.agregarParticipante(crearUsuarioConId(1L));

    return actividad;
  }

  private Usuario crearUsuarioConId(Long id)
  {
    Usuario usuario = new Usuario("usuario" + id, "password", TipoRol.USER);
    usuario.setId(id);
    return usuario;
  }
}
