package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurarClimaFlujoHandlerTest
{
  private static final long CHAT_ID = 999L;
  private static final String USUARIO_ID = "usr-1";
  private static final String ACTIVIDAD_ID = "act-1";
  private static final int MAX_DIAS_FORECAST = 14;

  @Mock
  private TelegramBot telegramBot;

  @Mock
  private TelegramSesionMongoRepository sesionRepository;

  @Mock
  private ActividadesService actividadesService;

  private ConfigurarClimaFlujoHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new ConfigurarClimaFlujoHandler(telegramBot, sesionRepository, actividadesService, MAX_DIAS_FORECAST);
    org.mockito.Mockito.lenient().when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
  }

  private TelegramSesionEntity sesionEnPaso(String paso)
  {
    TelegramSesionEntity sesion = new TelegramSesionEntity(CHAT_ID, USUARIO_ID);
    sesion.setFlujoActual(ConfigurarClimaFlujoHandler.FLUJO);
    sesion.setPasoActual(paso);
    sesion.setDatosParciales("{\"actividadId\":\"" + ACTIVIDAD_ID + "\"}");
    return sesion;
  }

  private Message mensaje(String texto)
  {
    Message mensaje = mock(Message.class);
    when(mensaje.text()).thenReturn(texto);
    return mensaje;
  }

  private ActividadDto actividadActualizada()
  {
    return new ActividadDto(ACTIVIDAD_ID, "Asado", null, TipoActividad.AIRE_LIBRE, null,
        LocalDateTime.of(2026, 12, 1, 18, 0), 3, 4, 10, null, null, 24, null, null, null, null);
  }

  @Test
  void lluviaConNumeroLaGuardaYAvanzaATempMinima()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_LLUVIA);

    handler.manejar(mensaje("70"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_TEMP_MINIMA);
    assertThat(sesion.getDatosParciales()).contains("maxProbabilidadLluvia");
    verify(sesionRepository).save(sesion);
  }

  @Test
  void lluviaConGuionOmiteElCampoPeroAvanza()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_LLUVIA);

    handler.manejar(mensaje("-"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_TEMP_MINIMA);
    assertThat(sesion.getDatosParciales()).doesNotContain("maxProbabilidadLluvia");
  }

  @Test
  void lluviaNegativaRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_LLUVIA);

    handler.manejar(mensaje("-70"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_LLUVIA);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void lluviaMayorA100RepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_LLUVIA);

    handler.manejar(mensaje("150"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_LLUVIA);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void vientoNegativoRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_VIENTO);

    handler.manejar(mensaje("-50"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_VIENTO);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void tempMaximaMenorALaMinimaYaGuardadaRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_TEMP_MAXIMA);
    sesion.setDatosParciales("{\"actividadId\":\"" + ACTIVIDAD_ID + "\",\"minTemperatura\":\"15.0\"}");

    handler.manejar(mensaje("10"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_TEMP_MAXIMA);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void tempMaximaSinMinimaGuardadaNoRestringeElValor()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_TEMP_MAXIMA);

    handler.manejar(mensaje("-10"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_VIENTO);
    assertThat(sesion.getDatosParciales()).contains("maxTemperatura");
  }

  @Test
  void lluviaConTextoInvalidoRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_LLUVIA);

    handler.manejar(mensaje("mucho"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_LLUVIA);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void anticipacionValidaAvanzaAPreguntarSiSeQuiereReprogramacion()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_ANTICIPACION);

    handler.manejar(mensaje("24"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_RANGO_PREGUNTA);
    assertThat(sesion.getDatosParciales()).contains("horasAnticipacion");
  }

  @Test
  void textoLibreEnPasoRangoPreguntaPideUsarLosBotones()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_PREGUNTA);

    handler.manejar(mensaje("si"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_RANGO_PREGUNTA);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void respuestaSiAvanzaAPreguntarLosDiasDeRango()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_PREGUNTA);
    sesion.setDatosParciales("{\"actividadId\":\"" + ACTIVIDAD_ID + "\",\"horasAnticipacion\":\"24\"}");

    String respuesta = handler.manejarRespuestaRango(sesion, true, USUARIO_ID);

    assertThat(respuesta).contains("reprogramacion");
    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_RANGO_DIAS);
    verify(actividadesService, never()).actualizarConfiguracionClima(any(), any(), any());
  }

  @Test
  void respuestaNoConfiguraElClimaDirectamenteSinRango()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_PREGUNTA);
    sesion.setDatosParciales("{\"actividadId\":\"" + ACTIVIDAD_ID + "\",\"horasAnticipacion\":\"24\"}");
    when(actividadesService.actualizarConfiguracionClima(org.mockito.ArgumentMatchers.eq(ACTIVIDAD_ID),
        org.mockito.ArgumentMatchers.eq(USUARIO_ID), any()))
        .thenReturn(actividadActualizada());

    handler.manejarRespuestaRango(sesion, false, USUARIO_ID);

    ArgumentCaptor<ConfigurarCondicionesDto> dtoCaptor = ArgumentCaptor.forClass(ConfigurarCondicionesDto.class);
    verify(actividadesService).actualizarConfiguracionClima(org.mockito.ArgumentMatchers.eq(ACTIVIDAD_ID),
        org.mockito.ArgumentMatchers.eq(USUARIO_ID), dtoCaptor.capture());
    assertThat(dtoCaptor.getValue().rangoReprogramacion()).isNull();
    assertThat(dtoCaptor.getValue().horasAnticipacion()).isEqualTo(24);
    verify(sesionRepository).deleteById(CHAT_ID);
  }

  @Test
  void respuestaEnPasoQueNoEsPreguntaDeRangoQuedaInvalida()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_DIAS);

    String respuesta = handler.manejarRespuestaRango(sesion, true, USUARIO_ID);

    assertThat(respuesta).isEqualTo("Esa opcion ya no es valida.");
  }

  @Test
  void anticipacionInvalidaRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_ANTICIPACION);

    handler.manejar(mensaje("0"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_ANTICIPACION);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void rangoDiasNegativoRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_DIAS);

    handler.manejar(mensaje("-1"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_RANGO_DIAS);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void anticipacionMayorAlMaximoDelPronosticoRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_ANTICIPACION);

    handler.manejar(mensaje(String.valueOf(MAX_DIAS_FORECAST * 24 + 1)), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_ANTICIPACION);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void anticipacionEnElLimiteDelMaximoAvanza()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_ANTICIPACION);

    handler.manejar(mensaje(String.valueOf(MAX_DIAS_FORECAST * 24)), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_RANGO_PREGUNTA);
  }

  @Test
  void rangoDiasMayorAlMaximoDelPronosticoRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_DIAS);

    handler.manejar(mensaje(String.valueOf(MAX_DIAS_FORECAST + 1)), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_RANGO_DIAS);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void rangoDiasEnElLimiteDelMaximoAvanza()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_DIAS);

    handler.manejar(mensaje(String.valueOf(MAX_DIAS_FORECAST)), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_RANGO_HORA_INICIO);
  }

  @Test
  void horaFueraDeRangoRepreguntaSinAvanzar()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_HORA_INICIO);

    handler.manejar(mensaje("24"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_RANGO_HORA_INICIO);
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void horaFinMenorOIgualALaDeInicioRepreguntaSinCrearNada()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_HORA_FIN);
    sesion.setDatosParciales("{\"actividadId\":\"" + ACTIVIDAD_ID + "\",\"horasAnticipacion\":\"24\","
        + "\"rangoDias\":\"3\",\"rangoHoraInicio\":\"10\"}");

    handler.manejar(mensaje("10"), sesion, USUARIO_ID);

    assertThat(sesion.getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_RANGO_HORA_FIN);
    verify(actividadesService, never()).actualizarConfiguracionClima(any(), any(), any());
    verify(sesionRepository, never()).deleteById(any(Long.class));
  }

  @Test
  void flujoCompletoConTodosLosValoresLlamaAActualizarConfiguracionClimaConElDtoEsperadoYBorraLaSesion()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_HORA_FIN);
    sesion.setDatosParciales("{\"actividadId\":\"" + ACTIVIDAD_ID + "\","
        + "\"maxProbabilidadLluvia\":\"70.0\",\"minTemperatura\":\"10.0\","
        + "\"maxTemperatura\":\"30.0\",\"maxViento\":\"40.0\","
        + "\"horasAnticipacion\":\"24\",\"rangoDias\":\"3\",\"rangoHoraInicio\":\"9\"}");
    when(actividadesService.actualizarConfiguracionClima(org.mockito.ArgumentMatchers.eq(ACTIVIDAD_ID),
        org.mockito.ArgumentMatchers.eq(USUARIO_ID), any()))
        .thenReturn(actividadActualizada());

    handler.manejar(mensaje("20"), sesion, USUARIO_ID);

    ArgumentCaptor<ConfigurarCondicionesDto> dtoCaptor = ArgumentCaptor.forClass(ConfigurarCondicionesDto.class);
    verify(actividadesService).actualizarConfiguracionClima(org.mockito.ArgumentMatchers.eq(ACTIVIDAD_ID),
        org.mockito.ArgumentMatchers.eq(USUARIO_ID), dtoCaptor.capture());

    ConfigurarCondicionesDto dto = dtoCaptor.getValue();
    assertThat(dto.reglasClima().maxProbabilidadLluvia()).isEqualTo(70.0);
    assertThat(dto.reglasClima().minTemperatura()).isEqualTo(10.0);
    assertThat(dto.reglasClima().maxTemperatura()).isEqualTo(30.0);
    assertThat(dto.reglasClima().maxViento()).isEqualTo(40.0);
    assertThat(dto.horasAnticipacion()).isEqualTo(24);
    assertThat(dto.rangoReprogramacion().dias()).isEqualTo(3);
    assertThat(dto.rangoReprogramacion().horaInicio()).isEqualTo(9);
    assertThat(dto.rangoReprogramacion().horaFinal()).isEqualTo(20);

    verify(sesionRepository).deleteById(CHAT_ID);
  }

  @Test
  void flujoCompletoOmitiendoLasReglasDeClimaDejaEsosCamposEnNull()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_HORA_FIN);
    sesion.setDatosParciales("{\"actividadId\":\"" + ACTIVIDAD_ID + "\","
        + "\"horasAnticipacion\":\"24\",\"rangoDias\":\"3\",\"rangoHoraInicio\":\"9\"}");
    when(actividadesService.actualizarConfiguracionClima(org.mockito.ArgumentMatchers.eq(ACTIVIDAD_ID),
        org.mockito.ArgumentMatchers.eq(USUARIO_ID), any()))
        .thenReturn(actividadActualizada());

    handler.manejar(mensaje("20"), sesion, USUARIO_ID);

    ArgumentCaptor<ConfigurarCondicionesDto> dtoCaptor = ArgumentCaptor.forClass(ConfigurarCondicionesDto.class);
    verify(actividadesService).actualizarConfiguracionClima(org.mockito.ArgumentMatchers.eq(ACTIVIDAD_ID),
        org.mockito.ArgumentMatchers.eq(USUARIO_ID), dtoCaptor.capture());

    ConfigurarCondicionesDto dto = dtoCaptor.getValue();
    assertThat(dto.reglasClima().maxProbabilidadLluvia()).isNull();
    assertThat(dto.reglasClima().minTemperatura()).isNull();
    assertThat(dto.reglasClima().maxTemperatura()).isNull();
    assertThat(dto.reglasClima().maxViento()).isNull();
    assertThat(dto.horasAnticipacion()).isEqualTo(24);

    verify(sesionRepository).deleteById(CHAT_ID);
  }

  @Test
  void datosParcialesIncompletosEnElUltimoPasoReiniciaLaSesionSinLlamarAlService()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_HORA_FIN);
    sesion.setDatosParciales("{\"actividadId\":\"" + ACTIVIDAD_ID + "\"}");

    handler.manejar(mensaje("20"), sesion, USUARIO_ID);

    verify(actividadesService, never()).actualizarConfiguracionClima(any(), any(), any());
    verify(sesionRepository).deleteById(CHAT_ID);
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("/clima");
  }

  @Test
  void siElServiceRechazaPorNoSerElOrganizadorAvisaYLimpiaLaSesion()
  {
    TelegramSesionEntity sesion = sesionEnPaso(ConfigurarClimaFlujoHandler.PASO_RANGO_HORA_FIN);
    sesion.setDatosParciales("{\"actividadId\":\"" + ACTIVIDAD_ID + "\","
        + "\"horasAnticipacion\":\"24\",\"rangoDias\":\"3\",\"rangoHoraInicio\":\"9\"}");
    when(actividadesService.actualizarConfiguracionClima(org.mockito.ArgumentMatchers.eq(ACTIVIDAD_ID),
        org.mockito.ArgumentMatchers.eq(USUARIO_ID), any()))
        .thenThrow(new com.tacs.backend.exceptions.AccesoDenegadoException("Solo el organizador puede configurar el clima"));

    handler.manejar(mensaje("20"), sesion, USUARIO_ID);

    verify(sesionRepository).deleteById(CHAT_ID);
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("Solo el organizador");
  }
}
