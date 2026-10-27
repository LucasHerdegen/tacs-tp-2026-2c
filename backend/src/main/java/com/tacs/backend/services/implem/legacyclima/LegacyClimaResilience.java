package com.tacs.backend.services.implem.legacyclima;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.RespuestaClimaInvalidaException;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima;
import com.tacs.backend.services.implem.clima.MonitorProveedoresClima;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Composicion funcional de Rate Limiter por usuario + Rate Limiter global hacia
 * el proveedor + Circuit Breaker + Retry (con backoff exponencial y jitter)
 * para las llamadas HTTP al servicio legacy de clima.
 */
@Component
public class LegacyClimaResilience
{
  private final CircuitBreaker circuitBreaker;
  private final RateLimiter rateLimiterGlobal;
  private final Retry retry;
  private final MonitorProveedoresClima monitor;
  private final RateLimiterConfig configPorUsuario;
  private final Map<String, RateLimiter> limitadoresPorUsuario = new ConcurrentHashMap<>();

  public LegacyClimaResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry,
      int limitePorUsuarioPeriodo,
      Duration periodoRefrescoUsuario)
  {
    this(
        circuitBreakerRegistry,
        rateLimiterRegistry,
        retryRegistry,
        null,
        limitePorUsuarioPeriodo,
        periodoRefrescoUsuario);
  }

  @Autowired
  public LegacyClimaResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry,
      MonitorProveedoresClima monitor,
      @Value("${legacy.clima.ratelimit.usuario.limit-for-period:15}") int limitePorUsuarioPeriodo,
      @Value("${legacy.clima.ratelimit.usuario.limit-refresh-period:1s}") Duration periodoRefrescoUsuario)
  {
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("legacyClima");
    this.rateLimiterGlobal = rateLimiterRegistry.rateLimiter("legacyClima");
    this.retry = retryRegistry.retry("legacyClima");
    this.monitor = monitor;
    this.configPorUsuario = RateLimiterConfig.custom()
        .limitForPeriod(limitePorUsuarioPeriodo)
        .limitRefreshPeriod(periodoRefrescoUsuario)
        .timeoutDuration(Duration.ZERO)
        .build();
  }

  public <T> T execute(Supplier<T> llamadaHttp)
  {
    return execute(ContextoProveedorClima.obtenerUsuarioId(), llamadaHttp);
  }

  public <T> T execute(String usuarioId, Supplier<T> llamadaHttp)
  {
    Supplier<T> supplierConResiliencia = construirDecorador(usuarioId, llamadaHttp);
    T resultado = supplierConResiliencia.get();
    if (monitor != null)
      monitor.registrarExito(TipoProveedorClima.LEGACY);
    return resultado;
  }

  private <T> Supplier<T> construirDecorador(String usuarioId, Supplier<T> llamadaHttp)
  {
    Supplier<T> llamadaConRegistroPorIntento = () ->
    {
      try
      {
        return llamadaHttp.get();
      } catch (RuntimeException ex)
      {
        registrarIntentoFallido(ex);
        throw ex;
      }
    };

    var builder = Decorators.ofSupplier(llamadaConRegistroPorIntento);
    if (usuarioId != null && !usuarioId.isBlank())
    {
      RateLimiter limitadorUsuario = limitadoresPorUsuario.computeIfAbsent(
          usuarioId,
          id -> RateLimiter.of("legacyClima-usuario-" + id, configPorUsuario));
      builder = builder.withRateLimiter(limitadorUsuario);
    }

    return builder
        .withRateLimiter(rateLimiterGlobal)
        .withCircuitBreaker(circuitBreaker)
        .withRetry(retry)
        .withFallback(List.of(Exception.class), throwable ->
        {
          registrarRechazoSiCorresponde(throwable);
          throw new ProveedorClimaIndisponibleException(
              "Proveedor de clima legacy no disponible temporalmente", throwable);
        })
        .decorate();
  }

  private void registrarIntentoFallido(Throwable throwable)
  {
    if (monitor == null)
      return;
    if (throwable instanceof RespuestaClimaInvalidaException)
      monitor.registrarErrorRespuestaInvalida(TipoProveedorClima.LEGACY, throwable.getMessage());
    else if (esTimeout(throwable))
      monitor.registrarErrorTimeout(TipoProveedorClima.LEGACY, "Timeout esperando respuesta del proveedor legacy");
    else
      monitor.registrarErrorGeneral(TipoProveedorClima.LEGACY, throwable.getMessage());
  }

  private void registrarRechazoSiCorresponde(Throwable throwable)
  {
    if (monitor == null)
      return;
    if (throwable instanceof RequestNotPermitted)
      monitor.registrarRechazoRateLimit(TipoProveedorClima.LEGACY, "Rate limit superado hacia el proveedor legacy");
    else if (throwable instanceof CallNotPermittedException)
      monitor.registrarErrorGeneral(TipoProveedorClima.LEGACY, "Circuit breaker abierto en proveedor legacy");
  }

  private boolean esTimeout(Throwable throwable)
  {
    Throwable actual = throwable;
    while (actual != null)
    {
      if (actual instanceof ResourceAccessException || actual instanceof SocketTimeoutException)
        return true;
      if (actual.getMessage() != null && actual.getMessage().toLowerCase().contains("timeout"))
        return true;
      actual = actual.getCause();
    }
    return false;
  }
}
