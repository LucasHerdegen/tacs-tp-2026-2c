package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import com.tacs.backend.services.ActividadesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrearActividadFlujoHandlerTest
{
  private static final long CHAT_ID = 999L;
  private static final String USUARIO_ID = "usr-1";

  @Mock
  private TelegramBot telegramBot;

  @Mock
  private TelegramSesionMongoRepository sesionRepository;

  @Mock
  private ActividadesService actividadesService;

  private CrearActividadFlujoHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new CrearActividadFlujoHandler(telegramBot, sesionRepository, actividadesService);
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
  }

  private TelegramSesionEntity sesionEnPaso(String paso)
  {
    TelegramSesionEntity sesion = new TelegramSesionEntity(CHAT_ID, USUARIO_ID);
    sesion.setFlujoActual(CrearActividadFlujoHandler.FLUJO);
    sesion.setPasoActual(paso);
    return sesion;
  }

  private Message mensaje(String texto)
  {
    Message mensaje = mock(Message.class);
    when(mensaje.text()).thenReturn(texto);
    return mensaje;
  }

  @Test
  void tituloValidoAvanzaAlPasoTipoYConservaElDato()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_TITULO);

    handler.manejar(mensaje("Asado en el parque"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_TIPO);
    assertThat(sesion.getDatosParciales()).contains("Asado en el parque");
    verify(sesionRepository).save(sesion);
  }

  @Test
  void tituloVacioRepreguntaElMismoPasoSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_TITULO);

    handler.manejar(mensaje("   "), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_TITULO);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void seleccionDeTipoAvanzaAlPasoUbicacionYConservaLosDatosPrevios()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_TIPO);
    sesion.setDatosParciales("{\"titulo\":\"Asado\"}");

    String respuesta = handler.manejarSeleccionTipo(sesion, TipoActividad.AIRE_LIBRE);

    assertThat(respuesta).contains("AIRE_LIBRE");
    assertThat(sesion.getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_UBICACION);
    assertThat(sesion.getDatosParciales()).contains("Asado").contains("AIRE_LIBRE");
  }

  @Test
  void ubicacionVaciaRepreguntaSinPerderElTituloYaCargado()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_UBICACION);
    sesion.setDatosParciales("{\"titulo\":\"Asado\",\"tipoActividad\":\"AIRE_LIBRE\"}");

    handler.manejar(mensaje(""), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_UBICACION);
    assertThat(sesion.getDatosParciales()).contains("Asado");
  }

  @Test
  void fechaConFormatoInvalidoRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_FECHA);

    handler.manejar(mensaje("mañana a la tarde"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_FECHA);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void fechaEnElPasadoRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_FECHA);
    String fechaPasada = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("dd:MM:yyyy HH:mm"));

    handler.manejar(mensaje(fechaPasada), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_FECHA);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void fechaValidaFuturaAvanzaAlPasoDuracion()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_FECHA);
    String fechaFutura = LocalDateTime.now().plusDays(5).format(DateTimeFormatter.ofPattern("dd:MM:yyyy HH:mm"));

    handler.manejar(mensaje(fechaFutura), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_DURACION);
  }

  @Test
  void duracionNoNumericaRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_DURACION);

    handler.manejar(mensaje("un ratito"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_DURACION);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void cupoMaximoMenorAlMinimoRepreguntaSinAvanzarNiCrearNada()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_CUPO_MAXIMO);
    sesion.setDatosParciales("{"
        + "\"titulo\":\"Asado\","
        + "\"tipoActividad\":\"AIRE_LIBRE\","
        + "\"ciudad\":\"Palermo\","
        + "\"fecha\":\"2026-12-01T18:00:00\","
        + "\"duracionEstimada\":\"3\","
        + "\"cantidadMinima\":\"10\"}");

    handler.manejar(mensaje("3"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_CUPO_MAXIMO);
    verify(actividadesService, never()).createActividad(any(), any());
    verify(sesionRepository, never()).deleteById(any(Long.class));
  }

  @Test
  void flujoCompletoLlamaACreateActividadConElDtoEsperadoYBorraLaSesion()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_CUPO_MAXIMO);
    sesion.setDatosParciales("{"
        + "\"titulo\":\"Asado en el parque\","
        + "\"tipoActividad\":\"AIRE_LIBRE\","
        + "\"ciudad\":\"Palermo\","
        + "\"fecha\":\"2026-12-01T18:00:00\","
        + "\"duracionEstimada\":\"3\","
        + "\"cantidadMinima\":\"4\"}");

    ActividadDto creada = new ActividadDto("act-1", "Asado en el parque", null, TipoActividad.AIRE_LIBRE,
        null, LocalDateTime.of(2026, 12, 1, 18, 0), 3, 4, 10, null, null, 24, null, null, null, null);
    when(actividadesService.createActividad(any(), any())).thenReturn(creada);

    handler.manejar(mensaje("10"), sesion, USUARIO_ID);

    ArgumentCaptor<ActividadPostDto> dtoCaptor = ArgumentCaptor.forClass(ActividadPostDto.class);
    verify(actividadesService).createActividad(dtoCaptor.capture(), org.mockito.ArgumentMatchers.eq(USUARIO_ID));

    ActividadPostDto dto = dtoCaptor.getValue();
    assertThat(dto.titulo()).isEqualTo("Asado en el parque");
    assertThat(dto.tipoActividad()).isEqualTo(TipoActividad.AIRE_LIBRE);
    assertThat(dto.ubicacion().getCiudad()).isEqualTo("Palermo");
    assertThat(dto.fecha()).isEqualTo(LocalDateTime.of(2026, 12, 1, 18, 0));
    assertThat(dto.duracionEstimada()).isEqualTo(3);
    assertThat(dto.cantidadMinima()).isEqualTo(4);
    assertThat(dto.cantidadMaxima()).isEqualTo(10);

    verify(sesionRepository).deleteById(CHAT_ID);
  }

  @Test
  void datosParcialesIncompletosEnElUltimoPasoReiniciaLaSesionSinCrearNada()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_CUPO_MAXIMO);
    sesion.setDatosParciales("{\"titulo\":\"Asado\"}");

    handler.manejar(mensaje("10"), sesion, USUARIO_ID);

    verify(actividadesService, never()).createActividad(any(), any());
    verify(sesionRepository).deleteById(CHAT_ID);
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("/crear");
  }

  @Test
  void siElArmadoDelDtoFallaPorUnDatoInvalidoAvisaYLimpiaLaSesion()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_CUPO_MAXIMO);
    sesion.setDatosParciales("{"
        + "\"titulo\":\"Asado\","
        + "\"tipoActividad\":\"NO_EXISTE\","
        + "\"ciudad\":\"Palermo\","
        + "\"fecha\":\"2026-12-01T18:00:00\","
        + "\"duracionEstimada\":\"3\","
        + "\"cantidadMinima\":\"4\"}");

    handler.manejar(mensaje("10"), sesion, USUARIO_ID);

    verify(actividadesService, never()).createActividad(any(), any());
    verify(sesionRepository).deleteById(CHAT_ID);
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("No pude crear la actividad");
  }

  @Test
  void siCreateActividadFallaAvisaElErrorYLimpiaLaSesion()
  {
    TelegramSesionEntity sesion = sesionEnPaso(CrearActividadFlujoHandler.PASO_CUPO_MAXIMO);
    sesion.setDatosParciales("{"
        + "\"titulo\":\"Asado\","
        + "\"tipoActividad\":\"AIRE_LIBRE\","
        + "\"ciudad\":\"Palermo\","
        + "\"fecha\":\"2026-12-01T18:00:00\","
        + "\"duracionEstimada\":\"3\","
        + "\"cantidadMinima\":\"4\"}");

    when(actividadesService.createActividad(any(), any()))
        .thenThrow(new RuntimeException("boom"));

    handler.manejar(mensaje("10"), sesion, USUARIO_ID);

    verify(sesionRepository).deleteById(CHAT_ID);
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("boom");
  }
}
