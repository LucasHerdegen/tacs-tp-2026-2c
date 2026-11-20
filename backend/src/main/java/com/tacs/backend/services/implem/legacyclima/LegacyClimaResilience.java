package com.tacs.backend.services.implem.legacyclima;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.RateLimitClimaExcedidoException;
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
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Composicion funcional de resiliencia para las llamadas HTTP al servicio
 * legacy de clima. En {@link Decorators}, cada {@code with...()} envuelve al
 * decorador anterior, por lo que el orden efectivo de ejecucion (de afuera
 * hacia adentro) es:
 * {@code Fallback -> RateLimiter (usuario) -> RateLimiter (global) -> CircuitBreaker -> TimeLimiter -> Retry -> llamada HTTP}.
 */
@Component
public class LegacyClimaResilience
{
  private static final Duration TIMEOUT_DEFAULT_PRUEBAS = Duration.ofSeconds(5);

  private final CircuitBreaker circuitBreaker;
  private final RateLimiter rateLimiterGlobal;
  private final Retry retry;
  private final TimeLimiter timeLimiter;
  private final MonitorProveedoresClima monitor;
  private final RateLimiterConfig configPorUsuario;
  private final Map<String, RateLimiter> limitadoresPorUsuario = new ConcurrentHashMap<>();
  private final ExecutorService ejecutor = Executors.newVirtualThreadPerTaskExecutor();

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
        timeLimiterRegistryPorDefecto(),
        null,
        limitePorUsuarioPeriodo,
        periodoRefrescoUsuario);
  }

  public LegacyClimaResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry,
      MonitorProveedoresClima monitor,
      int limitePorUsuarioPeriodo,
      Duration periodoRefrescoUsuario)
  {
    this(
        circuitBreakerRegistry,
        rateLimiterRegistry,
        retryRegistry,
        timeLimiterRegistryPorDefecto(),
        monitor,
        limitePorUsuarioPeriodo,
        periodoRefrescoUsuario);
  }

  @Autowired
  public LegacyClimaResilience(
      CircuitBreakerRegistry circuitBreakerRegistry,
      RateLimiterRegistry rateLimiterRegistry,
      RetryRegistry retryRegistry,
      TimeLimiterRegistry timeLimiterRegistry,
      MonitorProveedoresClima monitor,
      @Value("${legacy.clima.ratelimit.usuario.limit-for-period:5}") int limitePorUsuarioPeriodo,
      @Value("${legacy.clima.ratelimit.usuario.limit-refresh-period:1s}") Duration periodoRefrescoUsuario)
  {
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("legacyClima");
    this.rateLimiterGlobal = rateLimiterRegistry.rateLimiter("legacyClima");
    this.retry = retryRegistry.retry("legacyClima");
    this.timeLimiter = timeLimiterRegistry.timeLimiter("legacyClima");
    this.monitor = monitor;
    this.configPorUsuario = RateLimiterConfig.custom()
        .limitForPeriod(limitePorUsuarioPeriodo)
        .limitRefreshPeriod(periodoRefrescoUsuario)
        .timeoutDuration(Duration.ZERO)
        .build();
  }

  private static TimeLimiterRegistry timeLimiterRegistryPorDefecto()
  {
    return TimeLimiterRegistry.of(TimeLimiterConfig.custom()
        .timeoutDuration(TIMEOUT_DEFAULT_PRUEBAS)
        .cancelRunningFuture(true)
        .build());
  }

  @PreDestroy
  public void cerrarEjecutor()
  {
    ejecutor.shutdownNow();
  }

  public <T> T execute(Supplier<T> llamadaHttp)
  {
    return execute(ContextoProveedorClima.obtenerUsuarioId(), llamadaHttp);
  }

  public <T> T execute(String usuarioId, Supplier<T> llamadaHttp)
  {
    Duration tiempoRestante = ContextoProveedorClima.obtenerTiempoRestante();
    if (tiempoRestante != null && tiempoRestante.isZero())
    {
      TimeoutException timeout = new TimeoutException(
          "Presupuesto maximo de tiempo agotado antes de invocar al proveedor legacy");
      if (monitor != null)
        monitor.registrarErrorTimeout(TipoProveedorClima.LEGACY, timeout.getMessage());
      throw new ProveedorClimaIndisponibleException(
          "Tiempo maximo de respuesta excedido en el proveedor de clima legacy", timeout);
    }

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

    Supplier<T> conRetry = Decorators.ofSupplier(llamadaConRegistroPorIntento)
        .withRetry(retry)
        .decorate();

    Supplier<T> conTimeLimiter = () -> ejecutarConLimiteTiempo(conRetry);

    // En Resilience4j Decorators cada with...() envuelve al anterior:
    // Retry -> TimeLimiter -> CircuitBreaker -> RateLimiter global -> RateLimiter usuario -> Fallback.
    var builder = Decorators.ofSupplier(conTimeLimiter)
        .withCircuitBreaker(circuitBreaker)
        .withRateLimiter(rateLimiterGlobal);

    if (usuarioId != null && !usuarioId.isBlank())
    {
      RateLimiter limitadorUsuario = limitadoresPorUsuario.computeIfAbsent(
          usuarioId,
          id -> RateLimiter.of("legacyClima-usuario-" + id, configPorUsuario));
      builder = builder.withRateLimiter(limitadorUsuario);
    }

    return builder
        .withFallback(List.of(Exception.class), throwable ->
        {
          Throwable causa = throwable instanceof TimeoutOperacionInternaException && throwable.getCause() != null
              ? throwable.getCause()
              : throwable;
          registrarRechazoSiCorresponde(causa);
          if (causa instanceof RequestNotPermitted)
            throw new RateLimitClimaExcedidoException(
                "Limite de solicitudes excedido hacia el proveedor de clima legacy", causa);
          if (causa instanceof TimeoutException)
            throw new ProveedorClimaIndisponibleException(
                "Tiempo maximo de respuesta excedido en el proveedor de clima legacy", causa);
          throw new ProveedorClimaIndisponibleException(
              "Proveedor de clima legacy no disponible temporalmente", causa);
        })
        .decorate();
  }

  private <T> T ejecutarConLimiteTiempo(Supplier<T> operacion)
  {
    Duration tiempoRestante = ContextoProveedorClima.obtenerTiempoRestante();
    if (tiempoRestante != null && tiempoRestante.isZero())
      throw new TimeoutOperacionInternaException(
          new TimeoutException("Presupuesto maximo de tiempo agotado en el proveedor legacy"));

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
          new TimeoutException("Operacion interrumpida al esperar al proveedor legacy"));
    } catch (Exception ex)
    {
      throw new IllegalStateException("Error ejecutando llamada con limite de tiempo en proveedor legacy", ex);
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
    else if (throwable instanceof TimeoutException)
      monitor.registrarErrorTimeout(TipoProveedorClima.LEGACY, "Tiempo maximo de operacion excedido en proveedor legacy");
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
      if (actual.getMessage() != null && actual.getMessage().toLowerCase().contains("timeout"))
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
