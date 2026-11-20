package com.tacs.backend.services.implem.legacyclima;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadisticasProveedorClimaDto;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.RespuestaClimaInvalidaException;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima;
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
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

  @Test
  @DisplayName("Retry corre adentro del CircuitBreaker: fallos recuperados en reintento cuentan como 1 exito y no abren el circuito")
  void execute_fallosRecuperadosEnReintento_cuentaUnExitoEnCircuitBreakerYNoAbreCircuito()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(2)
        .minimumNumberOfCalls(2)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(20))
        .ignoreExceptions(RequestNotPermitted.class, HttpClientErrorException.class)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(1))
        .build());

    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        5,
        Duration.ofSeconds(1));

    for (int llamada = 0; llamada < 2; llamada++)
    {
      AtomicInteger intentosEnLlamada = new AtomicInteger();
      String resultado = resilience.execute("user-1", () ->
      {
        if (intentosEnLlamada.incrementAndGet() < 3)
          throw new RespuestaClimaInvalidaException("Respuesta corrupta en intento " + intentosEnLlamada.get());
        return "ok";
      });
      assertThat(resultado).isEqualTo("ok");
      assertThat(intentosEnLlamada.get()).isEqualTo(3);
    }

    CircuitBreaker cb = cbRegistry.circuitBreaker("legacyClima");
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cb.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
  }

  @Test
  @DisplayName("Un 401 no se reintenta ni cuenta como falla en el CircuitBreaker aunque supere minimumNumberOfCalls")
  void execute_error401ConTokenInvalido_noReintentaYNoAbreCircuitBreaker()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(3)
        .minimumNumberOfCalls(3)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(20))
        .ignoreExceptions(RequestNotPermitted.class, HttpClientErrorException.class)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(1))
        .ignoreExceptions(HttpClientErrorException.class)
        .build());

    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        10,
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

    for (int i = 0; i < 5; i++)
      assertThatThrownBy(() -> resilience.execute("user-1", falla401))
          .isInstanceOf(ProveedorClimaIndisponibleException.class);

    CircuitBreaker cb = cbRegistry.circuitBreaker("legacyClima");
    assertThat(invocaciones.get()).isEqualTo(5);
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
    assertThat(cb.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(0);
  }

  @Test
  @DisplayName("El rate limit por usuario se evalua antes que el global y aisla al usuario abusivo sin consumir cupo global")
  void execute_usuarioAbusivoExcedeLimitePorUsuario_seDisparaAntesQueGlobalYNoConsumeCupoGlobal()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .ignoreExceptions(RequestNotPermitted.class, HttpClientErrorException.class)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
        .limitForPeriod(4)
        .limitRefreshPeriod(Duration.ofSeconds(10))
        .timeoutDuration(Duration.ZERO)
        .build());
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(3).build());
    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, null);

    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        monitor,
        2,
        Duration.ofSeconds(10));

    // El usuario abusivo consume sus 2 permisos (y 2 de los 4 globales).
    assertThat(resilience.execute("usuario-abusivo", () -> "ok-1")).isEqualTo("ok-1");
    assertThat(resilience.execute("usuario-abusivo", () -> "ok-2")).isEqualTo("ok-2");

    // 3 intentos extra del usuario abusivo son bloqueados por el limitador por usuario
    // antes de tocar el limitador global (conservando los 2 permisos globales restantes).
    for (int i = 0; i < 3; i++)
      assertThatThrownBy(() -> resilience.execute("usuario-abusivo", () -> "bloqueado"))
          .isInstanceOf(ProveedorClimaIndisponibleException.class)
          .hasCauseInstanceOf(RequestNotPermitted.class);

    // Otro usuario puede usar integramente los 2 permisos globales que quedaron libres.
    assertThat(resilience.execute("usuario-normal", () -> "normal-1")).isEqualTo("normal-1");
    assertThat(resilience.execute("usuario-normal", () -> "normal-2")).isEqualTo("normal-2");

    EstadisticasProveedorClimaDto stats = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(stats.rechazosRateLimit()).isEqualTo(3);
  }

  @Test
  @DisplayName("TimeLimiter corta una llamada colgada en tiempo acotado, cancela el futuro y registra timeout en monitor y CB")
  void execute_operacionSuperaTimeLimiter_cortaEnTiempoAcotadoCancelaTareaYRegistraTimeout() throws Exception
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(4)
        .minimumNumberOfCalls(4)
        .failureRateThreshold(50)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(50))
        .build());
    TimeLimiterRegistry tlRegistry = TimeLimiterRegistry.of(TimeLimiterConfig.custom()
        .timeoutDuration(Duration.ofMillis(120))
        .cancelRunningFuture(true)
        .build());
    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, null);

    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        tlRegistry,
        monitor,
        10,
        Duration.ofSeconds(1));

    CountDownLatch tareaInterrumpida = new CountDownLatch(1);
    AtomicInteger intentosIniciados = new AtomicInteger();

    long inicioNanos = System.nanoTime();
    try
    {
      assertThatThrownBy(() -> resilience.execute("user-1", () ->
      {
        intentosIniciados.incrementAndGet();
        try
        {
          Thread.sleep(2000);
          return "tarde";
        } catch (InterruptedException e)
        {
          tareaInterrumpida.countDown();
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrumpido por TimeLimiter", e);
        }
      }))
          .isInstanceOf(ProveedorClimaIndisponibleException.class)
          .hasCauseInstanceOf(TimeoutException.class);
    } finally
    {
      resilience.cerrarEjecutor();
    }

    long duracionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos);
    assertThat(duracionMs).isLessThan(700L);
    assertThat(tareaInterrumpida.await(500, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(intentosIniciados.get()).isEqualTo(1);

    CircuitBreaker cb = cbRegistry.circuitBreaker("legacyClima");
    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);

    EstadisticasProveedorClimaDto stats = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(stats.timeouts()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("Si el presupuesto de tiempo total de la request ya se agoto, corta de inmediato sin invocar al proveedor ni afectar el CB")
  void execute_presupuestoTotalAgotado_cortaDeInmediatoSinInvocarProveedor()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(3).build());
    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        10,
        Duration.ofSeconds(1));

    AtomicInteger invocaciones = new AtomicInteger();
    ContextoProveedorClima.iniciarConPresupuesto("user-1", TipoProveedorClima.LEGACY, Duration.ZERO);
    try
    {
      assertThatThrownBy(() -> resilience.execute("user-1", () ->
      {
        invocaciones.incrementAndGet();
        return "no-deberia-correr";
      }))
          .isInstanceOf(ProveedorClimaIndisponibleException.class)
          .hasCauseInstanceOf(TimeoutException.class);
    } finally
    {
      ContextoProveedorClima.limpiar();
      resilience.cerrarEjecutor();
    }

    CircuitBreaker cb = cbRegistry.circuitBreaker("legacyClima");
    assertThat(invocaciones.get()).isEqualTo(0);
    assertThat(cb.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
  }

  @Test
  @DisplayName("Cuando el presupuesto restante en el contexto es menor que el TimeLimiter del proveedor, se corta al vencer el presupuesto")
  void execute_presupuestoRestanteMenorQueTimeLimiter_cortaSegunPresupuestoRestante() throws Exception
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.ofDefaults();
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
    TimeLimiterRegistry tlRegistry = TimeLimiterRegistry.of(TimeLimiterConfig.custom()
        .timeoutDuration(Duration.ofSeconds(5))
        .cancelRunningFuture(true)
        .build());
    LegacyClimaResilience resilience = new LegacyClimaResilience(
        cbRegistry,
        rlRegistry,
        retryRegistry,
        tlRegistry,
        null,
        10,
        Duration.ofSeconds(1));

    CountDownLatch tareaInterrumpida = new CountDownLatch(1);
    ContextoProveedorClima.iniciarConPresupuesto("user-1", TipoProveedorClima.LEGACY, Duration.ofMillis(120));
    long inicioNanos = System.nanoTime();
    try
    {
      assertThatThrownBy(() -> resilience.execute("user-1", () ->
      {
        try
        {
          Thread.sleep(2000);
          return "tarde";
        } catch (InterruptedException e)
        {
          tareaInterrumpida.countDown();
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }))
          .isInstanceOf(ProveedorClimaIndisponibleException.class)
          .hasCauseInstanceOf(TimeoutException.class);
    } finally
    {
      ContextoProveedorClima.limpiar();
      resilience.cerrarEjecutor();
    }

    long duracionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos);
    assertThat(duracionMs).isLessThan(700L);
    assertThat(tareaInterrumpida.await(500, TimeUnit.MILLISECONDS)).isTrue();
  }
}
