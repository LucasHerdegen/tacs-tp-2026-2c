package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.response.BaseResponse;
import com.tacs.backend.exceptions.TelegramEnvioPermanenteException;
import com.tacs.backend.exceptions.TelegramEnvioTransitorioException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Composicion funcional de Circuit Breaker + Retry
 * para cualquier llamada a la Bot API de Telegram que devuelva un
 * {@link BaseResponse} (sendMessage, answerCallbackQuery, etc.).
 * <p>
 * Pengrad no lanza excepcion ante un error de la API (ej. 403: bot bloqueado):
 * devuelve una respuesta con {@code isOk()=false}. Por eso la validacion de
 * la respuesta ocurre DENTRO del supplier decorado (no despues), traduciendo
 * a {@link TelegramEnvioPermanenteException} (4xx, no reintentable) o
 * {@link TelegramEnvioTransitorioException} (el resto, reintentable) - solo
 * asi Resilience4j Retry puede reaccionar a la falla.
 */
@Component
public class TelegramResilience
{
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  public TelegramResilience(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry)
  {
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("telegramApi");
    this.retry = retryRegistry.retry("telegramApi");
  }

  public <T extends BaseResponse> T ejecutar(Supplier<T> llamadaHttp)
  {
    Supplier<T> conValidacion = () ->
    {
      T response = llamadaHttp.get();
      if (!response.isOk())
      {
        throw mapearError(response);
      }
      return response;
    };

    Supplier<T> decorado = Decorators.ofSupplier(conValidacion)
        .withCircuitBreaker(circuitBreaker)
        .withRetry(retry)
        .decorate();

    return decorado.get();
  }

  private RuntimeException mapearError(BaseResponse response)
  {
    String mensaje = "Telegram respondio error %d: %s".formatted(response.errorCode(), response.description());

    if (response.errorCode() >= 400 && response.errorCode() < 500)
    {
      return new TelegramEnvioPermanenteException(mensaje);
    }
    return new TelegramEnvioTransitorioException(mensaje);
  }
}
