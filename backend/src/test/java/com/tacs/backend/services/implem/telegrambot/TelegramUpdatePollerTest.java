package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import com.tacs.backend.persistence.entities.TelegramUpdateOffsetEntity;
import com.tacs.backend.persistence.repositories.TelegramUpdateOffsetMongoRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramUpdatePollerTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private TelegramUpdateOffsetMongoRepository offsetRepository;

  @Mock
  private ComandoRouter comandoRouter;

  @Mock
  private TextoLibreRouter textoLibreRouter;

  @Mock
  private CallbackRouter callbackRouter;

  private TelegramUpdatePoller poller;

  @BeforeEach
  void setUp()
  {
    TelegramResilience telegramResilience = new TelegramResilience(
        CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults());
    poller = new TelegramUpdatePoller(telegramBot, telegramResilience, offsetRepository,
        comandoRouter, textoLibreRouter, callbackRouter, 25);
  }

  private Update comandoUpdate(int updateId, String texto)
  {
    Message mensaje = mock(Message.class);
    when(mensaje.text()).thenReturn(texto);

    Update update = mock(Update.class);
    when(update.updateId()).thenReturn(updateId);
    when(update.message()).thenReturn(mensaje);
    when(update.callbackQuery()).thenReturn(null);
    return update;
  }

  private GetUpdatesResponse respuestaOkCon(List<Update> updates)
  {
    GetUpdatesResponse response = mock(GetUpdatesResponse.class);
    when(response.isOk()).thenReturn(true);
    when(response.updates()).thenReturn(updates);
    return response;
  }

  @Test
  void primeraCorridaArrancaConOffsetCeroYPersisteElOffsetSiguiente()
  {
    when(offsetRepository.findById(TelegramUpdateOffsetEntity.ID_UNICO)).thenReturn(Optional.empty());
    Update update = comandoUpdate(100, "/start");
    GetUpdatesResponse response = respuestaOkCon(List.of(update));
    when(telegramBot.execute(any(GetUpdates.class))).thenReturn(response);

    poller.procesarUpdates();

    ArgumentCaptor<GetUpdates> captor = ArgumentCaptor.forClass(GetUpdates.class);
    verify(telegramBot).execute(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getParameters().get("offset")).isEqualTo(0);

    verify(comandoRouter).despachar(any(Message.class));

    ArgumentCaptor<TelegramUpdateOffsetEntity> offsetCaptor = ArgumentCaptor.forClass(TelegramUpdateOffsetEntity.class);
    verify(offsetRepository).save(offsetCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(offsetCaptor.getValue().getUltimoUpdateId()).isEqualTo(101);
  }

  @Test
  void segundaCorridaUsaElOffsetPersistidoYNoReprocesaElMismoUpdate()
  {
    when(offsetRepository.findById(TelegramUpdateOffsetEntity.ID_UNICO))
        .thenReturn(Optional.of(new TelegramUpdateOffsetEntity(101)));
    GetUpdatesResponse response = respuestaOkCon(List.of());
    when(telegramBot.execute(any(GetUpdates.class))).thenReturn(response);

    poller.procesarUpdates();

    ArgumentCaptor<GetUpdates> captor = ArgumentCaptor.forClass(GetUpdates.class);
    verify(telegramBot).execute(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getParameters().get("offset")).isEqualTo(101);

    verify(comandoRouter, never()).despachar(any());
    verify(offsetRepository, never()).save(any());
  }

  @Test
  void unUpdateQueRompeAlDespacharNoInterrumpeElRestoDelLote()
  {
    when(offsetRepository.findById(TelegramUpdateOffsetEntity.ID_UNICO)).thenReturn(Optional.empty());

    Update updateRoto = comandoUpdate(1, "/start");
    Update updateOk = comandoUpdate(2, "/ayuda");
    GetUpdatesResponse response = respuestaOkCon(List.of(updateRoto, updateOk));
    when(telegramBot.execute(any(GetUpdates.class))).thenReturn(response);
    org.mockito.Mockito.doThrow(new RuntimeException("boom"))
        .doNothing()
        .when(comandoRouter).despachar(any(Message.class));

    poller.procesarUpdates();

    verify(comandoRouter, times(2)).despachar(any(Message.class));
    ArgumentCaptor<TelegramUpdateOffsetEntity> offsetCaptor = ArgumentCaptor.forClass(TelegramUpdateOffsetEntity.class);
    verify(offsetRepository).save(offsetCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(offsetCaptor.getValue().getUltimoUpdateId()).isEqualTo(3);
  }

  @Test
  void callbackQueryLlegaAlCallbackRouterYNoAlComandoRouter()
  {
    when(offsetRepository.findById(TelegramUpdateOffsetEntity.ID_UNICO)).thenReturn(Optional.empty());
    CallbackQuery callbackQuery = mock(CallbackQuery.class);
    Update update = mock(Update.class);
    when(update.updateId()).thenReturn(5);
    when(update.callbackQuery()).thenReturn(callbackQuery);

    GetUpdatesResponse response = respuestaOkCon(List.of(update));
    when(telegramBot.execute(any(GetUpdates.class))).thenReturn(response);

    poller.procesarUpdates();

    verify(callbackRouter).despachar(callbackQuery);
    verify(comandoRouter, never()).despachar(any());
    verify(textoLibreRouter, never()).despachar(any());
  }
}
