package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.tacs.backend.exceptions.AccesoDenegadoException;
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
class BajarseCallbackHandlerTest
{
  @Mock
  private ActividadesService actividadesService;

  @Mock
  private Chat chat;

  @Mock
  private MaybeInaccessibleMessage maybeInaccessibleMessage;

  private BajarseCallbackHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new BajarseCallbackHandler(actividadesService);
  }

  private CallbackQuery callbackQuery(String actividadId)
  {
    org.mockito.Mockito.lenient().when(chat.id()).thenReturn(999L);
    org.mockito.Mockito.lenient().when(maybeInaccessibleMessage.chat()).thenReturn(chat);
    CallbackQuery callbackQuery = org.mockito.Mockito.mock(CallbackQuery.class);
    org.mockito.Mockito.lenient().when(callbackQuery.maybeInaccessibleMessage()).thenReturn(maybeInaccessibleMessage);
    org.mockito.Mockito.lenient().when(callbackQuery.data()).thenReturn("bajarse:" + actividadId);
    return callbackQuery;
  }

  @Test
  void elPrefijoQueManejaEsBajarse()
  {
    assertThat(handler.prefijo()).isEqualTo("bajarse");
  }

  @Test
  void sinUsuarioIdentificadoNoLlamaAlService()
  {
    String respuesta = handler.manejar(callbackQuery("act-1"), null);

    assertThat(respuesta).contains("/start");
  }

  @Test
  void bajarseExitosoLlamaAlServiceYConfirma()
  {
    String respuesta = handler.manejar(callbackQuery("act-1"), "usr-1");

    verify(actividadesService).bajarseActividad("act-1", "usr-1");
    assertThat(respuesta).isEqualTo("Te bajaste de la actividad.");
  }

  @Test
  void organizadorNoPuedeBajarseYTraduceLaExcepcion()
  {
    doThrow(new AccesoDenegadoException("El organizador no puede bajarse de la actividad"))
        .when(actividadesService).bajarseActividad("act-1", "usr-1");

    String respuesta = handler.manejar(callbackQuery("act-1"), "usr-1");

    assertThat(respuesta).doesNotContain("AccesoDenegadoException");
    assertThat(respuesta).contains("No pude bajarte");
    assertThat(respuesta).contains("organizador no puede bajarse");
  }
}
