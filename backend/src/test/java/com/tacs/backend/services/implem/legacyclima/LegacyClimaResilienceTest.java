package com.tacs.backend.services.implem.legacyclima;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadisticasProveedorClimaDto;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.RespuestaClimaInvalidaException;
import com.tacs.backend.services.implem.clima.MonitorProveedoresClima;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyClimaResilienceTest
{
  @Test
  void reintentaConBackoffExponencialYJitterAnteRespuestaMalformadaYRecuperaAlTercerIntento()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(5), 2.0, 0.5))
        .build());
    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, null);

    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        monitor,
        20,
        Duration.ofSeconds(1));

    AtomicInteger intentos = new AtomicInteger();
    String resultado = resilience.execute("user-1", () ->
    {
      int actual = intentos.incrementAndGet();
      if (actual < 3)
        throw new RespuestaClimaInvalidaException("JSON truncado en intento " + actual);
      return "pronostico-ok";
    });

    assertThat(resultado).isEqualTo("pronostico-ok");
    assertThat(intentos.get()).isEqualTo(3);

    EstadisticasProveedorClimaDto stats = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(stats.respuestasInvalidas()).isEqualTo(2);
    assertThat(stats.llamadasExitosas()).isEqualTo(1);
  }

  @Test
  void circuitBreakerSeAbreTrasAlcanzarUmbralDeFallasYEvitaLlamadasPosteriores()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(4)
        .minimumNumberOfCalls(4)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .ignoreExceptions(RequestNotPermitted.class)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, null);

    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        monitor,
        50,
        Duration.ofSeconds(1));

    AtomicInteger llamadasAlBinario = new AtomicInteger();
    Supplier<String> llamadaConTimeout = () ->
    {
      llamadasAlBinario.incrementAndGet();
      throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
    };

    for (int i = 0; i < 4; i++)
      assertThatThrownBy(() -> resilience.execute("user-1", llamadaConTimeout))
          .isInstanceOf(ProveedorClimaIndisponibleException.class);

    CircuitBreaker cb = cbRegistry.circuitBreaker("legacyClima");
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThat(llamadasAlBinario.get()).isEqualTo(4);

    assertThatThrownBy(() -> resilience.execute("user-1", llamadaConTimeout))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);
    assertThat(llamadasAlBinario.get()).isEqualTo(4);

    EstadisticasProveedorClimaDto stats = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(stats.timeouts()).isEqualTo(4);
    assertThat(stats.estadoCircuitBreaker()).isEqualTo("OPEN");
  }

  @Test
  void rateLimiterPorUsuarioAislaAUnUsuarioAbusivoSinBloquearAOtrosUsuarios()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .ignoreExceptions(RequestNotPermitted.class)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
        .limitForPeriod(100)
        .limitRefreshPeriod(Duration.ofSeconds(10))
        .timeoutDuration(Duration.ZERO)
        .build());
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, null);

    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        monitor,
        2,
        Duration.ofSeconds(10));

    assertThat(resilience.execute("usuario-abusivo", () -> "ok-1")).isEqualTo("ok-1");
    assertThat(resilience.execute("usuario-abusivo", () -> "ok-2")).isEqualTo("ok-2");

    assertThatThrownBy(() -> resilience.execute("usuario-abusivo", () -> "ok-3"))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);

    assertThat(resilience.execute("usuario-normal", () -> "ok-normal")).isEqualTo("ok-normal");

    EstadisticasProveedorClimaDto stats = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(stats.rechazosRateLimit()).isEqualTo(1);
  }

  @Test
  void error401NoSeReintentaPorSerErrorPermanenteDeAutenticacion()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(5))
        .ignoreExceptions(HttpClientErrorException.class)
        .build());

    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        20,
        Duration.ofSeconds(1));

    AtomicInteger invocaciones = new AtomicInteger();
    Supplier<String> falla401 = () ->
    {
      invocaciones.incrementAndGet();
      throw HttpClientErrorException.create(
          HttpStatus.UNAUTHORIZED,
          "Unauthorized",
          HttpHeaders.EMPTY,
          new byte[0],
          null);
    };

    assertThatThrownBy(() -> resilience.execute("user-1", falla401))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);
    assertThat(invocaciones.get()).isEqualTo(1);
  }
}
