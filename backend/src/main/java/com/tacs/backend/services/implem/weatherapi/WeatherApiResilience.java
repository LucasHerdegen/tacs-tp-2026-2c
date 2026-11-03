package com.tacs.backend.services.implem.weatherapi;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.services.implem.clima.MonitorProveedoresClima;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.function.Supplier;

/**
 * Composicion funcional (no anotaciones apiladas) de Rate Limiter + Circuit
 * Breaker + Retry para las llamadas HTTP reales a WeatherAPI. Se usa un solo
 * punto de entrada, {@link #execute}, invocado desde dentro del limite de
 * {@code @Cacheable} (nunca por fuera) para que un cache-hit jamas consuma
 * cupo de rate limit ni cuente para las metricas del circuit breaker.
 */
@Component
class WeatherApiResilience
{
  private final CircuitBreaker circuitBreaker;
  private final RateLimiter rateLimiter;
  private final Retry retry;
  private final MonitorProveedoresClima monitor;

  WeatherApiResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry)
  {
    this(circuitBreakerRegistry, rateLimiterRegistry, retryRegistry, null);
  }

  @Autowired
  WeatherApiResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry,
      MonitorProveedoresClima monitor)
  {
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("weatherApi");
    this.rateLimiter = rateLimiterRegistry.rateLimiter("weatherApi");
    this.retry = retryRegistry.retry("weatherApi");
    this.monitor = monitor;
  }

  <T> T execute(Supplier<T> llamadaHttp)
  {
    Supplier<T> decorate = Decorators.ofSupplier(llamadaHttp)
        .withRateLimiter(rateLimiter)
        .withCircuitBreaker(circuitBreaker)
        .withRetry(retry)
        .withFallback(List.of(Exception.class), throwable ->
        {
          registrarError(throwable);
          throw new ProveedorClimaIndisponibleException(
              "Proveedor de clima no disponible temporalmente", throwable);
        })
        .decorate();

    T resultado = decorate.get();
    if (monitor != null)
      monitor.registrarExito(TipoProveedorClima.WEATHER_API);
    return resultado;
  }

  private void registrarError(Throwable throwable)
  {
    if (monitor == null)
      return;
    if (throwable instanceof RequestNotPermitted)
      monitor.registrarRechazoRateLimit(TipoProveedorClima.WEATHER_API, "Rate limit excedido hacia WeatherAPI");
    else if (throwable instanceof ResourceAccessException)
      monitor.registrarErrorTimeout(TipoProveedorClima.WEATHER_API, "Timeout o error de red hacia WeatherAPI");
    else
      monitor.registrarErrorGeneral(TipoProveedorClima.WEATHER_API, throwable.getMessage());
  }
}
