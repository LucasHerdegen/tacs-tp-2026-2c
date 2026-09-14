package com.tacs.backend.domain.notificacion;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.exceptions.TelegramEnvioPermanenteException;
import com.tacs.backend.services.implem.telegrambot.TelegramResilience;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramTest
{
  @Mock
  private TelegramBot telegramBot;

  private TelegramResilience telegramResilienceSinReintentos;
  private TelegramResilience telegramResilienceConReintentos;

  @BeforeEach
  void setUp()
  {
    // max-attempts=1: sin reintentos, para aislar el comportamiento de un solo intento
    telegramResilienceSinReintentos = new TelegramResilience(
        CircuitBreakerRegistry.ofDefaults(),
        RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(1)
            .ignoreExceptions(TelegramEnvioPermanenteException.class)
            .build()));

    // max-attempts=3 con espera minima, para verificar que SI se reintenta lo transitorio
    telegramResilienceConReintentos = new TelegramResilience(
        CircuitBreakerRegistry.ofDefaults(),
        RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(1))
            .ignoreExceptions(TelegramEnvioPermanenteException.class)
            .build()));
  }

  @Test
  void envioExitosoLlamaSendMessageUnaVez()
  {
    SendResponse respuestaOk = mock(SendResponse.class);
    when(respuestaOk.isOk()).thenReturn(true);
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(respuestaOk);

    Telegram telegram = new Telegram(telegramBot, telegramResilienceSinReintentos);
    telegram.enviarNotificacion("hola", new MedioContacto("123", TipoMedioContacto.TELEGRAM));

    verify(telegramBot, times(1)).execute(any(SendMessage.class));
  }

  @Test
  void error403NoSeReintentaYPropagaExcepcionPermanente()
  {
    SendResponse bloqueado = mock(SendResponse.class);
    when(bloqueado.isOk()).thenReturn(false);
    when(bloqueado.errorCode()).thenReturn(403);
    when(bloqueado.description()).thenReturn("Forbidden: bot was blocked by the user");
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(bloqueado);

    Telegram telegram = new Telegram(telegramBot, telegramResilienceConReintentos);

    assertThatThrownBy(() ->
        telegram.enviarNotificacion("hola", new MedioContacto("123", TipoMedioContacto.TELEGRAM)))
        .isInstanceOf(TelegramEnvioPermanenteException.class);

    verify(telegramBot, times(1)).execute(any(SendMessage.class));
  }

  @Test
  void errorTransitorioSeReintentaAntesDeFallar()
  {
    SendResponse caido = mock(SendResponse.class);
    when(caido.isOk()).thenReturn(false);
    when(caido.errorCode()).thenReturn(500);
    when(caido.description()).thenReturn("Internal Server Error");
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(caido);

    Telegram telegram = new Telegram(telegramBot, telegramResilienceConReintentos);

    assertThatThrownBy(() ->
        telegram.enviarNotificacion("hola", new MedioContacto("123", TipoMedioContacto.TELEGRAM)))
        .isInstanceOf(com.tacs.backend.exceptions.TelegramEnvioTransitorioException.class);

    verify(telegramBot, times(3)).execute(any(SendMessage.class));
  }
}
