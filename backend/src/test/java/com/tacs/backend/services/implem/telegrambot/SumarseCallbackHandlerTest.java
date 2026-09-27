package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.tacs.backend.exceptions.CapacidadMaximaException;
import com.tacs.backend.exceptions.YaEsParticipanteException;
import com.tacs.backend.services.ActividadesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SumarseCallbackHandlerTest
{
  @Mock
  private ActividadesService actividadesService;

  @Mock
  private Chat chat;

  @Mock
  private MaybeInaccessibleMessage maybeInaccessibleMessage;

  private SumarseCallbackHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new SumarseCallbackHandler(actividadesService);
  }

  private CallbackQuery callbackQuery(String actividadId)
  {
    org.mockito.Mockito.lenient().when(chat.id()).thenReturn(999L);
    org.mockito.Mockito.lenient().when(maybeInaccessibleMessage.chat()).thenReturn(chat);
    CallbackQuery callbackQuery = org.mockito.Mockito.mock(CallbackQuery.class);
    org.mockito.Mockito.lenient().when(callbackQuery.maybeInaccessibleMessage()).thenReturn(maybeInaccessibleMessage);
    org.mockito.Mockito.lenient().when(callbackQuery.data()).thenReturn("sumarse:" + actividadId);
    return callbackQuery;
  }

  @Test
  void elPrefijoQueManejaEsSumarse()
  {
    assertThat(handler.prefijo()).isEqualTo("sumarse");
  }

  @Test
  void sinUsuarioIdentificadoNoLlamaAlService()
  {
    String respuesta = handler.manejar(callbackQuery("act-1"), null);

    assertThat(respuesta).contains("/start");
  }

  @Test
  void sumarseExitosoLlamaAlServiceYConfirma()
  {
    String respuesta = handler.manejar(callbackQuery("act-1"), "usr-1");

    verify(actividadesService).unirseActividad("act-1", "usr-1");
    assertThat(respuesta).isEqualTo("Te sumaste a la actividad.");
  }

  @Test
  void actividadLlenaTraduceLaExcepcionAMensajeDeChat()
  {
    doThrow(new CapacidadMaximaException("La actividad ya esta al maximo de participantes permitidos"))
        .when(actividadesService).unirseActividad("act-1", "usr-1");

    String respuesta = handler.manejar(callbackQuery("act-1"), "usr-1");

    assertThat(respuesta).doesNotContain("CapacidadMaximaException");
    assertThat(respuesta).contains("No pude sumarte");
    assertThat(respuesta).contains("maximo de participantes");
  }

  @Test
  void yaSerParticipanteTraduceLaExcepcionAMensajeDeChat()
  {
    doThrow(new YaEsParticipanteException("Ya sos participante de esta actividad"))
        .when(actividadesService).unirseActividad("act-1", "usr-1");

    String respuesta = handler.manejar(callbackQuery("act-1"), "usr-1");

    assertThat(respuesta).doesNotContain("YaEsParticipanteException");
    assertThat(respuesta).contains("No pude sumarte");
    assertThat(respuesta).contains("Ya sos participante");
  }

  @Test
  void callbackDataMalformadoNoRompeElHandler()
  {
    org.mockito.Mockito.lenient().when(chat.id()).thenReturn(999L);
    org.mockito.Mockito.lenient().when(maybeInaccessibleMessage.chat()).thenReturn(chat);
    CallbackQuery callbackQuery = org.mockito.Mockito.mock(CallbackQuery.class);
    org.mockito.Mockito.lenient().when(callbackQuery.maybeInaccessibleMessage()).thenReturn(maybeInaccessibleMessage);
    org.mockito.Mockito.lenient().when(callbackQuery.data()).thenReturn("sumarse");

    String respuesta = handler.manejar(callbackQuery, "usr-1");

    assertThat(respuesta).contains("No pude sumarte");
  }
}
