package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.response.BaseResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallbackRouterTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private TelegramIdentidadResolver identidadResolver;

  @Mock
  private CallbackQuery callbackQuery;

  @Mock
  private MaybeInaccessibleMessage maybeInaccessibleMessage;

  @Mock
  private Chat chat;

  @BeforeEach
  void setUp()
  {
    when(telegramBot.execute(any(AnswerCallbackQuery.class))).thenReturn(mock(BaseResponse.class));
    when(callbackQuery.id()).thenReturn("cbq-1");
  }

  @Test
  void callbackReconocidoDespachaAlHandlerYResponeConSuTexto()
  {
    when(callbackQuery.data()).thenReturn("sumarse:123");
    when(callbackQuery.maybeInaccessibleMessage()).thenReturn(maybeInaccessibleMessage);
    when(maybeInaccessibleMessage.chat()).thenReturn(chat);
    when(chat.id()).thenReturn(999L);
    when(identidadResolver.resolverUsuarioId(999L)).thenReturn(Optional.of("usr-1"));

    CallbackHandler handlerSumarse = mock(CallbackHandler.class);
    when(handlerSumarse.prefijo()).thenReturn("sumarse");
    when(handlerSumarse.manejar(callbackQuery, "usr-1")).thenReturn("Te sumaste con exito");

    CallbackRouter router = new CallbackRouter(telegramBot, identidadResolver, List.of(handlerSumarse));

    router.despachar(callbackQuery);

    ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getParameters().get("text")).isEqualTo("Te sumaste con exito");
  }

  @Test
  void callbackNoReconocidoNoInvocaNingunHandlerYResponeMensajeGenerico()
  {
    when(callbackQuery.data()).thenReturn("desconocido:1");

    CallbackHandler handlerSumarse = mock(CallbackHandler.class);
    when(handlerSumarse.prefijo()).thenReturn("sumarse");

    CallbackRouter router = new CallbackRouter(telegramBot, identidadResolver, List.of(handlerSumarse));

    router.despachar(callbackQuery);

    verify(handlerSumarse, org.mockito.Mockito.never()).manejar(any(), any());
    ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getParameters().get("text")).isEqualTo("No pude procesar esa accion.");
  }
}
