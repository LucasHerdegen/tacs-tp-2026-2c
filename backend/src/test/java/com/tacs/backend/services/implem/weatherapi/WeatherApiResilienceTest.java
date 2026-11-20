package com.tacs.backend.services.implem.weatherapi;

import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica la composicion funcional (Decorators) de Circuit Breaker + Rate
 * Limiter + Retry en aislamiento, sin RestClient ni red de por medio: cada
 * test arma sus propios registries con configuracion ajustada para forzar el
 * escenario en pocas llamadas, en vez de depender de application.properties.
 */
class WeatherApiResilienceTest
{
  @Test
  void trasNFallasConsecutivasElCircuitoAbreYFallaInstantaneo()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(4)
        .minimumNumberOfCalls(4)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofMinutes(1))
        .ignoreExceptions(RequestNotPermitted.class)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
        .limitForPeriod(1000)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO)
        .build());
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());

    WeatherApiResilience resilience = new WeatherApiResilience(cbRegistry, rlRegistry, retryRegistry);
    CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker("weatherApi");

    AtomicInteger invocacionesReales = new AtomicInteger();
    Supplier<String> siempreFalla = () ->
    {
      invocacionesReales.incrementAndGet();
      throw new RuntimeException("Fallo simulado de WeatherAPI");
    };

    for (int i = 0; i < 4; i++)
      assertThatThrownBy(() -> resilience.execute(siempreFalla))
          .isInstanceOf(ProveedorClimaIndisponibleException.class);

    assertThat(invocacionesReales.get()).isEqualTo(4);
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    // El circuito ya esta abierto: esta 5ta llamada debe fallar instantaneo,
    // sin siquiera invocar el supplier real (el contador no debe subir a 5).
    assertThatThrownBy(() -> resilience.execute(siempreFalla))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);
    assertThat(invocacionesReales.get()).isEqualTo(4);
  }

  @Test
  void alSuperarElLimiteDePeriodoElRateLimiterRechazaSinAfectarElCircuitBreaker()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(10)
        .minimumNumberOfCalls(10)
        .failureRateThreshold(50)
        .ignoreExceptions(RequestNotPermitted.class)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
        .limitForPeriod(2)
        .limitRefreshPeriod(Duration.ofSeconds(10))
        .timeoutDuration(Duration.ZERO)
        .build());
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());

    WeatherApiResilience resilience = new WeatherApiResilience(cbRegistry, rlRegistry, retryRegistry);
    CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker("weatherApi");

    Supplier<String> siempreExitosa = () -> "ok";

    assertThat(resilience.execute(siempreExitosa)).isEqualTo("ok");
    assertThat(resilience.execute(siempreExitosa)).isEqualTo("ok");

    // 3ra llamada dentro del mismo periodo: supera limit-for-period=2, se
    // rechaza instantaneo (timeout-duration=0, sin cola de espera).
    assertThatThrownBy(() -> resilience.execute(siempreExitosa))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);

    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
  }

  @Test
  void unHttpClientErrorExceptionNoSeReintentaPeroUnaFallaTransitoriaSi()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(1000)
        .minimumNumberOfCalls(1000)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
        .limitForPeriod(1000)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO)
        .build());
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(1))
        .ignoreExceptions(HttpClientErrorException.class)
        .build());

    WeatherApiResilience resilience = new WeatherApiResilience(cbRegistry, rlRegistry, retryRegistry);

    AtomicInteger invocaciones4xx = new AtomicInteger();
    Supplier<String> siempre401 = () ->
    {
      invocaciones4xx.incrementAndGet();
      throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY,
          new byte[0], null);
    };

    assertThatThrownBy(() -> resilience.execute(siempre401))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);
    // Ignorado por el retry: 1 sola invocacion real, no 3.
    assertThat(invocaciones4xx.get()).isEqualTo(1);

    AtomicInteger invocacionesTransitorias = new AtomicInteger();
    Supplier<String> siempreFallaTransitoria = () ->
    {
      invocacionesTransitorias.incrementAndGet();
      throw new RuntimeException("Timeout simulado");
    };

    assertThatThrownBy(() -> resilience.execute(siempreFallaTransitoria))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);
    // No esta en ignore-exceptions: se reintenta hasta max-attempts=3.
    assertThat(invocacionesTransitorias.get()).isEqualTo(3);
  }

  @Test
  @DisplayName("Retry corre adentro del CircuitBreaker: fallos recuperados en reintento cuentan como 1 exito y no abren el circuito")
  void execute_fallosRecuperadosEnReintento_cuentaUnExitoEnCircuitBreakerYNoAbreCircuito()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(2)
        .minimumNumberOfCalls(2)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .ignoreExceptions(RequestNotPermitted.class, HttpClientErrorException.class)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
        .limitForPeriod(100)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO)
        .build());
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(1))
        .build());

    WeatherApiResilience resilience = new WeatherApiResilience(cbRegistry, rlRegistry, retryRegistry);

    for (int llamada = 0; llamada < 2; llamada++)
    {
      AtomicInteger intentos = new AtomicInteger();
      String resultado = resilience.execute(() ->
      {
        if (intentos.incrementAndGet() < 3)
          throw new RuntimeException("Error transitorio 502 en intento " + intentos.get());
        return "ok";
      });
      assertThat(resultado).isEqualTo("ok");
      assertThat(intentos.get()).isEqualTo(3);
    }

    CircuitBreaker cb = cbRegistry.circuitBreaker("weatherApi");
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
  }

  @Test
  @DisplayName("Un 401 no se reintenta ni cuenta como falla en el CircuitBreaker de WeatherAPI")
  void execute_error401ApiKeyInvalida_noReintentaYNoCuentaComoFallaEnCircuitBreaker()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(3)
        .minimumNumberOfCalls(3)
        .failureRateThreshold(50)
        .ignoreExceptions(RequestNotPermitted.class, HttpClientErrorException.class)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
        .limitForPeriod(100)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO)
        .build());
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(1))
        .ignoreExceptions(HttpClientErrorException.class)
        .build());

    WeatherApiResilience resilience = new WeatherApiResilience(cbRegistry, rlRegistry, retryRegistry);

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

    for (int i = 0; i < 5; i++)
      assertThatThrownBy(() -> resilience.execute(falla401))
          .isInstanceOf(ProveedorClimaIndisponibleException.class);

    CircuitBreaker cb = cbRegistry.circuitBreaker("weatherApi");
    assertThat(invocaciones.get()).isEqualTo(5);
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
  }

  @Test
  @DisplayName("RateLimiter envuelve a CircuitBreaker y Retry: un rechazo no reintenta ni impacta al circuito")
  void execute_rateLimiterRechaza_noReintentaNiAfectaCircuitBreaker()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(2)
        .minimumNumberOfCalls(2)
        .failureRateThreshold(50)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
        .limitForPeriod(1)
        .limitRefreshPeriod(Duration.ofSeconds(10))
        .timeoutDuration(Duration.ZERO)
        .build());
    // Sin ignoreExceptions en Retry a proposito: como RateLimiter esta por fuera,
    // el rechazo jamas entra al bucle de reintentos ni al CircuitBreaker.
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(1))
        .build());

    WeatherApiResilience resilience = new WeatherApiResilience(cbRegistry, rlRegistry, retryRegistry);
    AtomicInteger invocaciones = new AtomicInteger();

    assertThat(resilience.execute(() ->
    {
      invocaciones.incrementAndGet();
      return "ok";
    })).isEqualTo("ok");

    assertThatThrownBy(() -> resilience.execute(() ->
    {
      invocaciones.incrementAndGet();
      return "no-deberia-ejecutar";
    }))
        .isInstanceOf(ProveedorClimaIndisponibleException.class)
        .hasCauseInstanceOf(RequestNotPermitted.class);

    CircuitBreaker cb = cbRegistry.circuitBreaker("weatherApi");
    assertThat(invocaciones.get()).isEqualTo(1);
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
    assertThat(cb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
  }
}
