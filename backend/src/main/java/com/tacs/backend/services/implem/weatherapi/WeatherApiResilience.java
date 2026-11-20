package com.tacs.backend.services.implem.weatherapi;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.RateLimitClimaExcedidoException;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima;
import com.tacs.backend.services.implem.clima.MonitorProveedoresClima;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Composicion funcional (no anotaciones apiladas) de resiliencia para las
 * llamadas HTTP reales a WeatherAPI. Se usa un solo punto de entrada,
 * {@link #execute}, invocado desde dentro del limite de {@code @Cacheable}
 * (nunca por fuera) para que un cache-hit jamas consuma cupo de rate limit ni
 * cuente para las metricas del circuit breaker.
 * <p>
 * Orden efectivo de ejecucion (de afuera hacia adentro):
 * {@code Fallback -> RateLimiter -> CircuitBreaker -> TimeLimiter -> Retry -> llamada HTTP}.
 */
@Component
class WeatherApiResilience
{
  private static final Duration TIMEOUT_DEFAULT_PRUEBAS = Duration.ofSeconds(5);

  private final CircuitBreaker circuitBreaker;
  private final RateLimiter rateLimiter;
  private final Retry retry;
  private final TimeLimiter timeLimiter;
  private final MonitorProveedoresClima monitor;
  private final ExecutorService ejecutor = Executors.newVirtualThreadPerTaskExecutor();

  WeatherApiResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry)
  {
    this(circuitBreakerRegistry, rateLimiterRegistry, retryRegistry, timeLimiterRegistryPorDefecto(), null);
  }

  WeatherApiResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry,
      MonitorProveedoresClima monitor)
  {
    this(circuitBreakerRegistry, rateLimiterRegistry, retryRegistry, timeLimiterRegistryPorDefecto(), monitor);
  }

  @Autowired
  WeatherApiResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry,
      TimeLimiterRegistry timeLimiterRegistry,
      MonitorProveedoresClima monitor)
  {
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("weatherApi");
    this.rateLimiter = rateLimiterRegistry.rateLimiter("weatherApi");
    this.retry = retryRegistry.retry("weatherApi");
    this.timeLimiter = timeLimiterRegistry.timeLimiter("weatherApi");
    this.monitor = monitor;
  }

  private static TimeLimiterRegistry timeLimiterRegistryPorDefecto()
  {
    return TimeLimiterRegistry.of(TimeLimiterConfig.custom()
        .timeoutDuration(TIMEOUT_DEFAULT_PRUEBAS)
        .cancelRunningFuture(true)
        .build());
  }

  @PreDestroy
  void cerrarEjecutor()
  {
    ejecutor.shutdownNow();
  }

  <T> T execute(Supplier<T> llamadaHttp)
  {
    Duration tiempoRestante = ContextoProveedorClima.obtenerTiempoRestante();
    if (tiempoRestante != null && tiempoRestante.isZero())
    {
      TimeoutException timeout = new TimeoutException(
          "Presupuesto maximo de tiempo agotado antes de invocar a WeatherAPI");
      registrarError(timeout);
      throw new ProveedorClimaIndisponibleException(
          "Tiempo maximo de respuesta excedido hacia WeatherAPI", timeout);
    }

    Supplier<T> conRetry = Decorators.ofSupplier(llamadaHttp)
        .withRetry(retry)
        .decorate();

    Supplier<T> conTimeLimiter = () -> ejecutarConLimiteTiempo(conRetry);

    // En Resilience4j Decorators cada with...() envuelve al anterior:
    // Retry -> TimeLimiter -> CircuitBreaker -> RateLimiter -> Fallback.
    Supplier<T> decorate = Decorators.ofSupplier(conTimeLimiter)
        .withCircuitBreaker(circuitBreaker)
        .withRateLimiter(rateLimiter)
        .withFallback(List.of(Exception.class), throwable ->
        {
          Throwable causa = throwable instanceof TimeoutOperacionInternaException && throwable.getCause() != null
              ? throwable.getCause()
              : throwable;
          registrarError(causa);
          if (causa instanceof RequestNotPermitted)
            throw new RateLimitClimaExcedidoException(
                "Limite de solicitudes excedido hacia WeatherAPI", causa);
          if (causa instanceof TimeoutException)
            throw new ProveedorClimaIndisponibleException(
                "Tiempo maximo de respuesta excedido hacia WeatherAPI", causa);
          throw new ProveedorClimaIndisponibleException(
              "Proveedor de clima no disponible temporalmente", causa);
        })
        .decorate();

    T resultado = decorate.get();
    if (monitor != null)
      monitor.registrarExito(TipoProveedorClima.WEATHER_API);
    return resultado;
  }

  private <T> T ejecutarConLimiteTiempo(Supplier<T> operacion)
  {
    Duration tiempoRestante = ContextoProveedorClima.obtenerTiempoRestante();
    if (tiempoRestante != null && tiempoRestante.isZero())
      throw new TimeoutOperacionInternaException(
          new TimeoutException("Presupuesto maximo de tiempo agotado en WeatherAPI"));

    TimeLimiter limitadorEfectivo = resolverTimeLimiterEfectivo(tiempoRestante);
    try
    {
      return limitadorEfectivo.executeFutureSupplier(() -> ejecutor.submit(operacion::get));
    } catch (RuntimeException ex)
    {
      throw ex;
    } catch (TimeoutException ex)
    {
      throw new TimeoutOperacionInternaException(ex);
    } catch (InterruptedException ex)
    {
      Thread.currentThread().interrupt();
      throw new TimeoutOperacionInternaException(
          new TimeoutException("Operacion interrumpida al esperar a WeatherAPI"));
    } catch (Exception ex)
    {
      throw new IllegalStateException("Error ejecutando llamada con limite de tiempo en WeatherAPI", ex);
    }
  }

  private TimeLimiter resolverTimeLimiterEfectivo(Duration tiempoRestante)
  {
    Duration configurado = timeLimiter.getTimeLimiterConfig().getTimeoutDuration();
    if (tiempoRestante == null || tiempoRestante.compareTo(configurado) >= 0)
      return timeLimiter;
    return TimeLimiter.of(TimeLimiterConfig.custom()
        .timeoutDuration(tiempoRestante)
        .cancelRunningFuture(true)
        .build());
  }

  private void registrarError(Throwable throwable)
  {
    if (monitor == null)
      return;
    if (throwable instanceof RequestNotPermitted)
      monitor.registrarRechazoRateLimit(TipoProveedorClima.WEATHER_API, "Rate limit excedido hacia WeatherAPI");
    else if (esTimeout(throwable))
      monitor.registrarErrorTimeout(TipoProveedorClima.WEATHER_API, "Timeout o error de red hacia WeatherAPI");
    else
      monitor.registrarErrorGeneral(TipoProveedorClima.WEATHER_API, throwable.getMessage());
  }

  private boolean esTimeout(Throwable throwable)
  {
    Throwable actual = throwable;
    while (actual != null)
    {
      if (actual instanceof ResourceAccessException
          || actual instanceof SocketTimeoutException
          || actual instanceof TimeoutException)
        return true;
      actual = actual.getCause();
    }
    return false;
  }

  private static final class TimeoutOperacionInternaException extends RuntimeException
  {
    private TimeoutOperacionInternaException(TimeoutException cause)
    {
      super(cause);
    }
  }
}
