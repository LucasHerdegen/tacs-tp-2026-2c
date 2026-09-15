package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.tacs.backend.exceptions.TelegramVinculacionInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartComandoHandlerTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private AltaNativaHandler altaNativaHandler;

  @Mock
  private VinculacionHandler vinculacionHandler;

  @Mock
  private Chat chat;

  private StartComandoHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new StartComandoHandler(telegramBot, altaNativaHandler, vinculacionHandler);
  }

  private Message mensaje(String texto)
  {
    Message mensaje = mock(Message.class);
    org.mockito.Mockito.lenient().when(chat.id()).thenReturn(999L);
    when(mensaje.chat()).thenReturn(chat);
    org.mockito.Mockito.lenient().when(mensaje.text()).thenReturn(texto);
    return mensaje;
  }

  @Test
  void elComandoQueManejaEsStart()
  {
    assertThat(handler.comando()).isEqualTo("/start");
  }

  @Test
  void startSinPayloadYSinUsuarioVinculadoHaceAltaNativa()
  {
    handler.manejar(mensaje("/start"), null);

    verify(altaNativaHandler).crearUsuarioNativo(999L);
    verify(vinculacionHandler, never()).vincular(anyLong(), any());
  }

  @Test
  void startConTokenVinculaLaCuentaExistenteEnVezDeCrearUnaNueva()
  {
    handler.manejar(mensaje("/start tok-123"), null);

    verify(vinculacionHandler).vincular(999L, "tok-123");
    verify(altaNativaHandler, never()).crearUsuarioNativo(anyLong());
  }

  @Test
  void startConTokenInvalidoRespondeElMensajeDeErrorEnElChatSinRomper()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    doThrow(new TelegramVinculacionInvalidaException("El link vencio"))
        .when(vinculacionHandler).vincular(999L, "tok-vencido");

    handler.manejar(mensaje("/start tok-vencido"), null);

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).isEqualTo("El link vencio");
  }

  @Test
  void startConUsuarioYaVinculadoNoHaceAltaNiVinculacionDeNuevo()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    handler.manejar(mensaje("/start"), "usr-1");

    verify(altaNativaHandler, never()).crearUsuarioNativo(anyLong());
    verify(vinculacionHandler, never()).vincular(anyLong(), any());
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).isEqualTo("Ya tenes una cuenta vinculada a este chat.");
  }
}
