package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComandoRouterTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private TelegramIdentidadResolver identidadResolver;

  @Mock
  private Chat chat;

  private Message mensaje(String texto)
  {
    Message mensaje = mock(Message.class);
    when(mensaje.chat()).thenReturn(chat);
    when(mensaje.text()).thenReturn(texto);
    return mensaje;
  }

  @BeforeEach
  void setUp()
  {
    when(chat.id()).thenReturn(999L);
  }

  @Test
  void comandoReconocidoDespachaAlHandlerCorrecto()
  {
    ComandoHandler handlerStart = mock(ComandoHandler.class);
    when(handlerStart.comando()).thenReturn("/start");
    ComandoHandler handlerAyuda = mock(ComandoHandler.class);
    when(handlerAyuda.comando()).thenReturn("/ayuda");

    when(identidadResolver.resolverUsuarioId(999L)).thenReturn(Optional.of("usr-1"));

    ComandoRouter router = new ComandoRouter(telegramBot, identidadResolver,
        List.of(handlerStart, handlerAyuda));

    router.despachar(mensaje("/start"));

    verify(handlerStart).manejar(any(Message.class), org.mockito.ArgumentMatchers.eq("usr-1"));
    verify(handlerAyuda, never()).manejar(any(), any());
  }

  @Test
  void comandoConSufijoDeBotYPayloadSeNormalizaAntesDeMatchear()
  {
    ComandoHandler handlerStart = mock(ComandoHandler.class);
    when(handlerStart.comando()).thenReturn("/start");
    when(identidadResolver.resolverUsuarioId(999L)).thenReturn(Optional.empty());

    ComandoRouter router = new ComandoRouter(telegramBot, identidadResolver, List.of(handlerStart));

    router.despachar(mensaje("/start@mi_bot token-123"));

    verify(handlerStart).manejar(any(Message.class), org.mockito.ArgumentMatchers.isNull());
  }

  @Test
  void comandoNoReconocidoRespondeConMensajeDeAyudaYNoInvocaNingunHandler()
  {
    ComandoHandler handlerStart = mock(ComandoHandler.class);
    when(handlerStart.comando()).thenReturn("/start");
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    ComandoRouter router = new ComandoRouter(telegramBot, identidadResolver, List.of(handlerStart));

    router.despachar(mensaje("/inexistente"));

    verify(handlerStart, never()).manejar(any(), any());
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("/ayuda");
  }
}
