package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import com.tacs.backend.services.VotacionesService;
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

@ExtendWith(MockitoExtension.class)
class ChequeoClimaJobTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);

  @Mock
  private ActividadesRepository actividadesRepository;

  @Mock
  private ProveedorClima proveedorClima;

  @Mock
  private ServicioNotificaciones servicioNotificaciones;

  @Mock
  private VotacionesService votacionesService;

  private ChequeoClimaJob job;

  private void inicializarJob()
  {
    job = new ChequeoClimaJob(actividadesRepository, proveedorClima, servicioNotificaciones, votacionesService);
  }

  @Test
  void detectaActividadConClimaDesfavorableDentroDeLaVentana()
  {
    Actividad actividad = crearActividad(
        LocalDateTime.now().plusHours(2), // fecha de la actividad
        24,                                // horasAnticipacion: la ventana ya arrancÃ³
        new ReglasClima(30, 10, 30, 20));  // max 30% de lluvia permitido

    Clima pronosticoMalo = new Clima(80, 20, 10); // 80% de probabilidad de lluvia

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividad));
    when(proveedorClima.obtenerPronostico(UBICACION, actividad.getFecha())).thenReturn(pronosticoMalo);

    inicializarJob();
    List<Actividad> resultado = job.detectarClimaDesfavorable();

    assertThat(resultado).containsExactly(actividad);
  }

  @Test
  void noDetectaActividadConClimaFavorableDentroDeLaVentana()
  {
    Actividad actividad = crearActividad(
        LocalDateTime.now().plusHours(2),
        24,
        new ReglasClima(30, 10, 30, 20));

    Clima pronosticoBueno = new Clima(5, 22, 10); // dentro de todos los limites

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividad));
    when(proveedorClima.obtenerPronostico(UBICACION, actividad.getFecha())).thenReturn(pronosticoBueno);

    inicializarJob();
    List<Actividad> resultado = job.detectarClimaDesfavorable();

    assertThat(resultado).isEmpty();
  }

  @Test
  void noEvaluaActividadesFueraDeLaVentanaDeAnticipacion()
  {
    Actividad actividad = crearActividad(
        LocalDateTime.now().plusDays(10), // muy lejos en el tiempo
        24,                                 // la ventana recien arranca 24hs antes
        new ReglasClima(30, 10, 30, 20));

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividad));

    inicializarJob();
    List<Actividad> resultado = job.detectarClimaDesfavorable();

    assertThat(resultado).isEmpty();
    verify(proveedorClima, never()).obtenerPronostico(any(), any());
  }

  @Test
  void unaFallaConsultandoElPronosticoDeUnaActividadNoImpideDetectarLasDemas()
  {
    Actividad actividadQueFalla = crearActividad(
        LocalDateTime.now().plusHours(2), 24, new ReglasClima(30, 10, 30, 20));
    Actividad actividadQueFunciona = crearActividad(
        LocalDateTime.now().plusHours(2), 24, new ReglasClima(30, 10, 30, 20));

    Clima pronosticoMalo = new Clima(80, 20, 10);

    when(actividadesRepository.findCandidatasParaChequeoClima())
        .thenReturn(List.of(actividadQueFalla, actividadQueFunciona));
    when(proveedorClima.obtenerPronostico(UBICACION, actividadQueFalla.getFecha()))
        .thenThrow(new RuntimeException("proveedor de clima caido"));
    when(proveedorClima.obtenerPronostico(UBICACION, actividadQueFunciona.getFecha()))
        .thenReturn(pronosticoMalo);

    inicializarJob();
    List<Actividad> resultado = job.detectarClimaDesfavorable(); // no debe propagar la excepcion

    assertThat(resultado).containsExactly(actividadQueFunciona);
  }

  @Test
  void notificaAParticipantesConMedioDeContactoCuandoElClimaEsDesfavorable()
  {
    Actividad actividad = crearActividad(
        LocalDateTime.now().plusHours(2),
        24,
        new ReglasClima(30, 10, 30, 20));

    MedioContacto medioContacto = new MedioContacto("123456789", TipoMedioContacto.TELEGRAM);
    Usuario participante = crearParticipante(medioContacto);
    actividad.agregarParticipante(participante);

    Clima pronosticoMalo = new Clima(80, 20, 10);

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividad));
    when(proveedorClima.obtenerPronostico(UBICACION, actividad.getFecha())).thenReturn(pronosticoMalo);

    inicializarJob();
    job.chequearClima();

    verify(servicioNotificaciones).notificar(contains(actividad.getTitulo()), eq(medioContacto));
  }

  @Test
  void noNotificaAParticipantesCuandoElClimaEsFavorable()
  {
    Actividad actividad = crearActividad(
        LocalDateTime.now().plusHours(2),
        24,
        new ReglasClima(30, 10, 30, 20));

    actividad.agregarParticipante(crearParticipante(new MedioContacto("123456789", TipoMedioContacto.TELEGRAM)));

    Clima pronosticoBueno = new Clima(5, 22, 10);

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividad));
    when(proveedorClima.obtenerPronostico(UBICACION, actividad.getFecha())).thenReturn(pronosticoBueno);

    inicializarJob();
    job.chequearClima();

    verify(servicioNotificaciones, never()).notificar(anyString(), any());
  }

  @Test
  void noNotificaAParticipanteSinMedioDeContactoConfigurado()
  {
    Actividad actividad = crearActividad(
        LocalDateTime.now().plusHours(2),
        24,
        new ReglasClima(30, 10, 30, 20));

    actividad.agregarParticipante(crearParticipante(null)); // sin medio de contacto

    Clima pronosticoMalo = new Clima(80, 20, 10);

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividad));
    when(proveedorClima.obtenerPronostico(UBICACION, actividad.getFecha())).thenReturn(pronosticoMalo);

    inicializarJob();
    job.chequearClima(); // no debe lanzar excepcion

    verify(servicioNotificaciones, never()).notificar(anyString(), any());
  }

  @Test
  void unaFallaNotificandoAUnParticipanteNoImpideNotificarAlResto()
  {
    Actividad actividad = crearActividad(
        LocalDateTime.now().plusHours(2),
        24,
        new ReglasClima(30, 10, 30, 20));

    MedioContacto medioQueFalla = new MedioContacto("111", TipoMedioContacto.TELEGRAM);
    MedioContacto medioQueFunciona = new MedioContacto("222", TipoMedioContacto.TELEGRAM);
    actividad.agregarParticipante(crearParticipante(medioQueFalla));
    actividad.agregarParticipante(crearParticipante(medioQueFunciona));

    Clima pronosticoMalo = new Clima(80, 20, 10);

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividad));
    when(proveedorClima.obtenerPronostico(UBICACION, actividad.getFecha())).thenReturn(pronosticoMalo);
    doThrow(new RuntimeException("Telegram caido"))
        .when(servicioNotificaciones).notificar(anyString(), eq(medioQueFalla));

    inicializarJob();
    job.chequearClima(); // no debe propagar la excepcion

    verify(servicioNotificaciones).notificar(anyString(), eq(medioQueFunciona));
  }

  @Test
  void abreVotacionAutomaticaCuandoDetectaClimaDesfavorable()
  {
    Actividad actividad = crearActividad(
        LocalDateTime.now().plusHours(2),
        24,
        new ReglasClima(30, 10, 30, 20));
    actividad.setId(99L);

    Clima pronosticoMalo = new Clima(80, 20, 10);

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividad));
    when(proveedorClima.obtenerPronostico(UBICACION, actividad.getFecha())).thenReturn(pronosticoMalo);

    inicializarJob();
    job.chequearClima();

    verify(votacionesService).abrirVotacionAutomatica(99L);
  }

  @Test
  void noAbreVotacionAutomaticaCuandoElClimaEsFavorable()
  {
    Actividad actividad = crearActividad(
        LocalDateTime.now().plusHours(2),
        24,
        new ReglasClima(30, 10, 30, 20));

    Clima pronosticoBueno = new Clima(5, 22, 10);

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividad));
    when(proveedorClima.obtenerPronostico(UBICACION, actividad.getFecha())).thenReturn(pronosticoBueno);

    inicializarJob();
    job.chequearClima();

    verify(votacionesService, never()).abrirVotacionAutomatica(any());
  }

  @Test
  void unaFallaAbriendoVotacionAutomaticaNoImpideProcesarLasDemasActividades()
  {
    Actividad actividadQueFalla = crearActividad(
        LocalDateTime.now().plusHours(2), 24, new ReglasClima(30, 10, 30, 20));
    actividadQueFalla.setId(1L);

    Actividad actividadQueFunciona = crearActividad(
        LocalDateTime.now().plusHours(2), 24, new ReglasClima(30, 10, 30, 20));
    actividadQueFunciona.setId(2L);

    Clima pronosticoMalo = new Clima(80, 20, 10);

    when(actividadesRepository.findCandidatasParaChequeoClima()).thenReturn(List.of(actividadQueFalla, actividadQueFunciona));
    when(proveedorClima.obtenerPronostico(eq(UBICACION), any())).thenReturn(pronosticoMalo);
    when(votacionesService.abrirVotacionAutomatica(1L)).thenThrow(new RuntimeException("fallo inesperado"));

    inicializarJob();
    job.chequearClima(); // no debe propagar la excepcion ni frenar el resto del loop

    verify(votacionesService).abrirVotacionAutomatica(2L);
  }

  private Actividad crearActividad(LocalDateTime fecha, int horasAnticipacion, ReglasClima reglasClima)
  {
    Actividad actividad = new Actividad(
        "Asado en el parque",
        "Actividad de prueba",
        TipoActividad.AIRE_LIBRE,
        UBICACION,
        fecha,
        LocalDateTime.now(),
        2,
        10,
        null);

    actividad.setHorasAnticipacion(horasAnticipacion);
    actividad.setReglasClima(reglasClima);

    return actividad;
  }

  private Usuario crearParticipante(MedioContacto medioContacto)
  {
    Usuario participante = new Usuario("participante", "password", TipoRol.USER);
    participante.setMedioContacto(medioContacto);
    return participante;
  }
}