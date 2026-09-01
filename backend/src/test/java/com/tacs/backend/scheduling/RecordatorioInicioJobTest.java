package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.services.ServicioNotificaciones;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Asume que RecordatorioInicioJob expone el constructor
 * (ActividadesRepository, ServicioNotificaciones, int horasAnticipacionDefault)
 * y que detectarActividadesPorComenzar() es package-private, para poder testear
 * la deteccion sin depender del scheduler ni de la notificacion.
 */
@ExtendWith(MockitoExtension.class)
class RecordatorioInicioJobTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);
  private static final int HORAS_ANTICIPACION_DEFAULT = 24;

  @Mock
  private ActividadesRepository actividadesRepository;

  @Mock
  private ServicioNotificaciones servicioNotificaciones;

  private RecordatorioInicioJob job;

  private void inicializarJob()
  {
    job = new RecordatorioInicioJob(
        actividadesRepository, servicioNotificaciones, HORAS_ANTICIPACION_DEFAULT);
  }

  @Test
  void detectaActividadDentroDeLaVentanaDeAnticipacion()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusHours(2), 24);

    when(actividadesRepository.findCandidatasParaRecordatorio()).thenReturn(List.of(actividad));

    inicializarJob();
    List<Actividad> resultado = job.detectarActividadesPorComenzar();

    assertThat(resultado).containsExactly(actividad);
  }

  @Test
  void noDetectaActividadTodaviaLejosDeSuVentanaDeAnticipacion()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusDays(10), 24);

    when(actividadesRepository.findCandidatasParaRecordatorio()).thenReturn(List.of(actividad));

    inicializarJob();
    List<Actividad> resultado = job.detectarActividadesPorComenzar();

    assertThat(resultado).isEmpty();
  }

  @Test
  void aplicaLaAnticipacionPorDefectoCuandoLaActividadNoLaTieneConfigurada()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusHours(10), 0);

    when(actividadesRepository.findCandidatasParaRecordatorio()).thenReturn(List.of(actividad));

    inicializarJob();
    List<Actividad> resultado = job.detectarActividadesPorComenzar();

    assertThat(resultado).containsExactly(actividad);
  }

  @Test
  void conLaAnticipacionPorDefectoTampocoNotificaLasQueSiguenLejos()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusHours(30), 0);

    when(actividadesRepository.findCandidatasParaRecordatorio()).thenReturn(List.of(actividad));

    inicializarJob();
    List<Actividad> resultado = job.detectarActividadesPorComenzar();

    assertThat(resultado).isEmpty();
  }

  @Test
  void notificaAlosParticipantesYMarcaElRecordatorioComoEnviado()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusHours(2), 24);

    when(actividadesRepository.findCandidatasParaRecordatorio()).thenReturn(List.of(actividad));

    inicializarJob();
    job.enviarRecordatorios();

    verify(servicioNotificaciones)
        .notificarATodos(contains(actividad.getTitulo()), eq(actividad.getParticipantes()));

    assertThat(actividad.isRecordatorioEnviado()).isTrue();
    verify(actividadesRepository).save(actividad);
  }

  @Test
  void elOrganizadorTambienRecibeElRecordatorio()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusHours(2), 24);

    when(actividadesRepository.findCandidatasParaRecordatorio()).thenReturn(List.of(actividad));

    inicializarJob();
    job.enviarRecordatorios();

    assertThat(actividad.getParticipantes()).contains(actividad.getOrganizador());
  }

  @Test
  void noNotificaLasActividadesQueTodaviaNoEntraronEnSuVentana()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusDays(10), 24);

    when(actividadesRepository.findCandidatasParaRecordatorio()).thenReturn(List.of(actividad));

    inicializarJob();
    job.enviarRecordatorios();

    verify(servicioNotificaciones, never()).notificarATodos(anyString(), any());
    assertThat(actividad.isRecordatorioEnviado()).isFalse();
  }

  @Test
  void siFallaLaNotificacionNoMarcaElRecordatorioNiGuarda()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusHours(2), 24);

    when(actividadesRepository.findCandidatasParaRecordatorio()).thenReturn(List.of(actividad));
    doThrow(new RuntimeException("Telegram caido"))
        .when(servicioNotificaciones).notificarATodos(anyString(), any());

    inicializarJob();
    job.enviarRecordatorios(); // no propaga la excepcion

    assertThat(actividad.isRecordatorioEnviado()).isFalse();
    verify(actividadesRepository, never()).save(any());
  }

  @Test
  void unaFallaNotificandoUnaActividadNoImpideProcesarLasDemas()
  {
    Actividad actividadQueFalla = crearActividad(LocalDateTime.now().plusHours(2), 24);
    actividadQueFalla.setTitulo("Actividad que falla");

    Actividad actividadQueFunciona = crearActividad(LocalDateTime.now().plusHours(2), 24);
    actividadQueFunciona.setTitulo("Actividad que funciona");

    when(actividadesRepository.findCandidatasParaRecordatorio())
        .thenReturn(List.of(actividadQueFalla, actividadQueFunciona));
    doThrow(new RuntimeException("Telegram caido"))
        .when(servicioNotificaciones).notificarATodos(contains("Actividad que falla"), any());

    inicializarJob();
    job.enviarRecordatorios();

    verify(servicioNotificaciones).notificarATodos(contains("Actividad que funciona"), any());
    assertThat(actividadQueFunciona.isRecordatorioEnviado()).isTrue();
    assertThat(actividadQueFalla.isRecordatorioEnviado()).isFalse();
  }

  /* ==================== Auxiliares ==================== */
  private Actividad crearActividad(LocalDateTime fechaRealizacion, int horasAnticipacion)
  {
    Actividad actividad = new Actividad(
        "Asado en el parque",
        "Actividad de prueba",
        TipoActividad.AIRE_LIBRE,
        UBICACION,
        fechaRealizacion,
        2,
        LocalDateTime.now(),
        2,
        10,
        crearUsuarioConId(999L));

    actividad.setEstado(TipoEstadoActividad.PROPUESTA);
    actividad.setHorasAnticipacion(horasAnticipacion);

    return actividad;
  }

  private Usuario crearUsuarioConId(Long id)
  {
    Usuario usuario = new Usuario("usuario" + id, "password", TipoRol.USER);
    usuario.setId(id);
    return usuario;
  }
}
