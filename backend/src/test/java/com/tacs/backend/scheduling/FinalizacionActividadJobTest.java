package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.notificacion.TipoNotificacion;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.mappers.ClimaMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.NotificacionInboxService;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import com.tacs.backend.services.implem.ActividadesServiceImplem;
import com.tacs.backend.services.implem.OptimisticLockingRetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalizacionActividadJobTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);

  @Mock
  private ActividadesRepository actividadesRepository;

  @Mock
  private VotacionesRepository votacionesRepository;

  @Mock
  private ServicioNotificaciones servicioNotificaciones;

  @Mock
  private NotificacionInboxService notificacionInboxService;

  private FinalizacionActividadJob job;

  @BeforeEach
  void setUp()
  {
    ActividadesService actividadesService = new ActividadesServiceImplem(
        actividadesRepository, mock(UsuarioRepository.class), mock(ActividadesMapper.class),
        mock(ProveedorClima.class), mock(ClimaMapper.class), servicioNotificaciones,
        notificacionInboxService, votacionesRepository, new OptimisticLockingRetry());
    job = new FinalizacionActividadJob(actividadesRepository, actividadesService);
  }

  @Test
  void detectaActividadesTerminadasCuandoFechaMasDuracionYaPaso()
  {
    LocalDateTime ahora = LocalDateTime.now();

    Actividad terminada = crearActividad("1", ahora.minusHours(4), 2, TipoEstadoActividad.CONFIRMADA);
    Actividad enCurso = crearActividad("2", ahora.minusHours(1), 3, TipoEstadoActividad.CONFIRMADA);

    when(actividadesRepository.findCandidatasParaFinalizacion(ahora))
        .thenReturn(List.of(terminada, enCurso));

    List<Actividad> resultado = job.detectarActividadesTerminadas(ahora);

    assertThat(resultado).containsExactly(terminada);
  }

  @Test
  void finalizaActividadConfirmadaCuandoTermina()
  {
    LocalDateTime ahora = LocalDateTime.now();
    Actividad confirmada = crearActividad("1", ahora.minusHours(3), 2, TipoEstadoActividad.CONFIRMADA);

    when(actividadesRepository.findCandidatasParaFinalizacion(any()))
        .thenReturn(List.of(confirmada));

    job.finalizarActividadesPasadas();

    assertThat(confirmada.getEstado()).isEqualTo(TipoEstadoActividad.FINALIZADA);
    verify(actividadesRepository).save(confirmada);
  }

  @Test
  void finalizaActividadReprogramadaCuandoTermina()
  {
    LocalDateTime ahora = LocalDateTime.now();
    Actividad reprogramada = crearActividad("2", ahora.minusHours(4), 2, TipoEstadoActividad.REPROGRAMADA);

    when(actividadesRepository.findCandidatasParaFinalizacion(any()))
        .thenReturn(List.of(reprogramada));

    job.finalizarActividadesPasadas();

    assertThat(reprogramada.getEstado()).isEqualTo(TipoEstadoActividad.FINALIZADA);
    verify(actividadesRepository).save(reprogramada);
  }

  @Test
  void cancelaActividadPropuestaCuandoLlegaLaFechaYNotifica()
  {
    LocalDateTime ahora = LocalDateTime.now();
    Actividad propuesta = crearActividad("3", ahora.minusHours(5), 2, TipoEstadoActividad.PROPUESTA);

    when(actividadesRepository.findCandidatasParaFinalizacion(any()))
        .thenReturn(List.of(propuesta));

    job.finalizarActividadesPasadas();

    assertThat(propuesta.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(actividadesRepository).save(propuesta);
    verify(servicioNotificaciones).notificarATodos(any(), eq(propuesta.getParticipantes()));
    verify(notificacionInboxService).crearParaTodos(any(), eq(TipoNotificacion.ACTIVIDAD_CANCELADA),
        eq("3"), eq(null), eq(propuesta.getParticipantes()));
  }

  @Test
  void cancelaActividadPropuestaConVotacionAbiertaCierraPrimeroLaVotacionLuegoGuardaYNotifica()
  {
    LocalDateTime ahora = LocalDateTime.now();
    Actividad propuesta = crearActividad("3", ahora.minusHours(5), 2, TipoEstadoActividad.PROPUESTA);

    Votacion votacionAbierta = new Votacion();
    votacionAbierta.setId("vot-3");
    votacionAbierta.setActividad(propuesta);
    votacionAbierta.setAbierta(true);

    when(actividadesRepository.findCandidatasParaFinalizacion(any()))
        .thenReturn(List.of(propuesta));
    when(votacionesRepository.findByAbiertaTrueAndActividadId("3"))
        .thenReturn(Optional.of(votacionAbierta));
    when(votacionesRepository.cerrarSiAbierta(votacionAbierta))
        .thenReturn(Optional.of(votacionAbierta));

    job.finalizarActividadesPasadas();

    assertThat(votacionAbierta.isAbierta()).isFalse();
    assertThat(propuesta.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);

    InOrder orden = inOrder(votacionesRepository, actividadesRepository, servicioNotificaciones, notificacionInboxService);
    orden.verify(votacionesRepository).cerrarSiAbierta(votacionAbierta);
    orden.verify(actividadesRepository).save(propuesta);
    orden.verify(servicioNotificaciones).notificarATodos(any(), eq(propuesta.getParticipantes()));
    orden.verify(notificacionInboxService).crearParaTodos(any(), eq(TipoNotificacion.ACTIVIDAD_CANCELADA),
        eq("3"), eq(null), eq(propuesta.getParticipantes()));
  }

  @Test
  void noFinalizaActividadCuyoFinAunNoOcurrio()
  {
    LocalDateTime ahora = LocalDateTime.now();
    Actividad enCurso = crearActividad("4", ahora.minusHours(1), 3, TipoEstadoActividad.CONFIRMADA);

    when(actividadesRepository.findCandidatasParaFinalizacion(any()))
        .thenReturn(List.of(enCurso));

    job.finalizarActividadesPasadas();

    assertThat(enCurso.getEstado()).isEqualTo(TipoEstadoActividad.CONFIRMADA);
    verify(actividadesRepository, never()).save(enCurso);
  }

  @Test
  void continuaProcesandoSiUnaActividadFalla()
  {
    LocalDateTime ahora = LocalDateTime.now();
    Actividad conFallo = crearActividad("1", ahora.minusHours(4), 2, TipoEstadoActividad.CONFIRMADA);
    Actividad exitosa = crearActividad("2", ahora.minusHours(4), 2, TipoEstadoActividad.CONFIRMADA);

    when(actividadesRepository.findCandidatasParaFinalizacion(any()))
        .thenReturn(List.of(conFallo, exitosa));
    doThrow(new RuntimeException("Error simulado de base de datos")).when(actividadesRepository).save(conFallo);

    job.finalizarActividadesPasadas();

    verify(actividadesRepository).save(conFallo);
    verify(actividadesRepository).save(exitosa);
    assertThat(exitosa.getEstado()).isEqualTo(TipoEstadoActividad.FINALIZADA);
  }

  private Actividad crearActividad(String id, LocalDateTime fechaRealizacion, int duracionEstimada,
                                  TipoEstadoActividad estado)
  {
    Usuario organizador = new Usuario("org" + id, "password", TipoRol.USER);
    organizador.setId("user-" + id);

    Actividad actividad = new Actividad(
        "Actividad " + id,
        "Descripcion " + id,
        TipoActividad.AIRE_LIBRE,
        UBICACION,
        fechaRealizacion,
        duracionEstimada,
        fechaRealizacion.minusDays(2),
        2,
        10,
        organizador
    );
    actividad.setId(id);
    actividad.setEstado(estado);
    return actividad;
  }
}
