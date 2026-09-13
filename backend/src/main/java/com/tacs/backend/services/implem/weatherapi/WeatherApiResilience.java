package com.tacs.backend.services.implem.weatherapi;

import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Composicion funcional (no anotaciones apiladas) de Rate Limiter + Circuit
 * Breaker + Retry para las llamadas HTTP reales a WeatherAPI. Se usa un solo
 * punto de entrada, {@link #execute}, invocado desde dentro del limite de
 * {@code @Cacheable} (nunca por fuera) para que un cache-hit jamas consuma
 * cupo de rate limit ni cuente para las metricas del circuit breaker.
 *
 * Orden de composicion (de adentro hacia afuera): RateLimiter envuelve la
 * llamada real, CircuitBreaker envuelve al RateLimiter, y Retry envuelve todo
 * el conjunto. Como el chequeo de estado del CircuitBreaker se evalua antes
 * de invocar lo que envuelve, en la practica esto significa que, con el
 * circuito abierto, el RateLimiter ni se llega a evaluar (el CircuitBreaker
 * corta primero); pero si el circuito esta cerrado y es el RateLimiter quien
 * rechaza, esa excepcion sí la ve el CircuitBreaker (la esta envolviendo) —
 * por eso su configuracion en application.properties ignora explicitamente
 * {@code RequestNotPermitted}, para que un rechazo por limite de requests no
 * cuente como falla del proveedor. Retry, a su vez, ignora tanto
 * {@code CallNotPermittedException} como {@code RequestNotPermitted}: un
 * rechazo instantaneo no debe generar reintentos (eso demoraria la respuesta
 * sin ningun beneficio real).
 */
@Component
class WeatherApiResilience
{
  private final CircuitBreaker circuitBreaker;
  private final RateLimiter rateLimiter;
  private final Retry retry;

  WeatherApiResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry)
  {
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("weatherApi");
    this.rateLimiter = rateLimiterRegistry.rateLimiter("weatherApi");
    this.retry = retryRegistry.retry("weatherApi");
  }

  /**
   * Ejecuta {@code llamadaHttp} protegida por rate limiter + circuit breaker
   * + retry. Cualquier rechazo (circuito abierto, limite de requests
   * superado) o falla persistente tras agotar los reintentos se traduce a
   * {@link ProveedorClimaIndisponibleException} — nunca se fabrica un
   * {@code Clima} de relleno aca.
   */
  <T> T execute(Supplier<T> llamadaHttp)
  {
    Supplier<T> decorate = Decorators.ofSupplier(llamadaHttp)
        .withRateLimiter(rateLimiter)
        .withCircuitBreaker(circuitBreaker)
        .withRetry(retry)
        .withFallback(List.of(Exception.class), throwable ->
        {
          throw new ProveedorClimaIndisponibleException(
              "Proveedor de clima no disponible temporalmente", throwable);
        })
        .decorate();

    return decorate.get();
  }
}
