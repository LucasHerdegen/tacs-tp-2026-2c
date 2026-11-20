package com.tacs.backend.services.implem.clima;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.RateLimitClimaExcedidoException;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima.DatosContexto;
import com.tacs.backend.services.implem.legacyclima.LegacyClimaResilience;
import com.tacs.backend.services.implem.legacyclima.LegacyProveedorClima;
import com.tacs.backend.services.implem.weatherapi.WeatherApiProveedorClima;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectorProveedorClimaTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);
  private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 28, 16, 0);

  @Mock
  private WeatherApiProveedorClima weatherApiProveedorClima;

  @Mock
  private LegacyProveedorClima legacyProveedorClima;

  private MonitorProveedoresClima monitor;
  private SelectorProveedorClima selector;

  @BeforeEach
  void setUp()
  {
    monitor = new MonitorProveedoresClima(CircuitBreakerRegistry.ofDefaults(), null);
    selector = new SelectorProveedorClima(weatherApiProveedorClima, legacyProveedorClima, monitor);
  }

  @AfterEach
  void tearDown()
  {
    ContextoProveedorClima.limpiar();
  }

  @Test
  void cuandoElUsuarioEligeWeatherApiUsaWeatherApiSinLlamarALegacy()
  {
    Clima esperado = new Clima(10.0, 22.0, 12.0);
    when(weatherApiProveedorClima.obtenerPronostico(UBICACION, FECHA)).thenReturn(esperado);

    ContextoProveedorClima.iniciar("user-1", TipoProveedorClima.WEATHER_API);
    Clima obtenido = selector.obtenerPronostico(UBICACION, FECHA);

    assertThat(obtenido).isEqualTo(esperado);
    verify(legacyProveedorClima, never()).obtenerPronostico(UBICACION, FECHA);
    DatosContexto snapshot = ContextoProveedorClima.obtenerSnapshot();
    assertThat(snapshot.proveedorSeleccionado()).isEqualTo(TipoProveedorClima.WEATHER_API);
    assertThat(snapshot.proveedorUtilizado()).isEqualTo(TipoProveedorClima.WEATHER_API);
    assertThat(snapshot.fallbackActivo()).isFalse();
  }

  @Test
  void cuandoElUsuarioEligeLegacyYFuncionaDevuelveElClimaDeLegacy()
  {
    Clima esperadoLegacy = new Clima(35.0, 19.5, 18.0);
    when(legacyProveedorClima.obtenerPronostico(UBICACION, FECHA)).thenReturn(esperadoLegacy);

    ContextoProveedorClima.iniciar("user-2", TipoProveedorClima.LEGACY);
    Clima obtenido = selector.obtenerPronostico(UBICACION, FECHA);

    assertThat(obtenido).isEqualTo(esperadoLegacy);
    verify(weatherApiProveedorClima, never()).obtenerPronostico(UBICACION, FECHA);
    DatosContexto snapshot = ContextoProveedorClima.obtenerSnapshot();
    assertThat(snapshot.proveedorSeleccionado()).isEqualTo(TipoProveedorClima.LEGACY);
    assertThat(snapshot.proveedorUtilizado()).isEqualTo(TipoProveedorClima.LEGACY);
    assertThat(snapshot.fallbackActivo()).isFalse();
  }

  @Test
  void cuandoElUsuarioEligeLegacyYFallaActivaFallbackAutomaticoAWeatherApiYRegistraMetrica()
  {
    Clima respaldoWeatherApi = new Clima(15.0, 21.0, 10.0);
    when(legacyProveedorClima.obtenerPronostico(UBICACION, FECHA))
        .thenThrow(new ProveedorClimaIndisponibleException("Legacy caido 503"));
    when(weatherApiProveedorClima.obtenerPronostico(UBICACION, FECHA))
        .thenReturn(respaldoWeatherApi);

    ContextoProveedorClima.iniciar("user-2", TipoProveedorClima.LEGACY);
    Clima obtenido = selector.obtenerPronostico(UBICACION, FECHA);

    assertThat(obtenido).isEqualTo(respaldoWeatherApi);
    DatosContexto snapshot = ContextoProveedorClima.obtenerSnapshot();
    assertThat(snapshot.proveedorSeleccionado()).isEqualTo(TipoProveedorClima.LEGACY);
    assertThat(snapshot.proveedorUtilizado()).isEqualTo(TipoProveedorClima.WEATHER_API);
    assertThat(snapshot.fallbackActivo()).isTrue();
    assertThat(snapshot.mensajeEstado()).contains("WeatherAPI");

    var statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsLegacy.fallbacksActivados()).isEqualTo(1);
  }

  @Test
  void cuandoFallaLegacyYTambienFallaElFallbackPropagaProveedorClimaIndisponibleException()
  {
    when(legacyProveedorClima.obtenerClima(UBICACION))
        .thenThrow(new ProveedorClimaIndisponibleException("Legacy caido"));
    when(weatherApiProveedorClima.obtenerClima(UBICACION))
        .thenThrow(new ProveedorClimaIndisponibleException("WeatherAPI tambien caido"));

    ContextoProveedorClima.iniciar("user-2", TipoProveedorClima.LEGACY);

    assertThatThrownBy(() -> selector.obtenerClima(UBICACION))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);
  }

  @Test
  @DisplayName("Si legacy rechaza por rate limit, activa fallback automatico a WeatherAPI")
  void obtenerClima_legacyRechazaPorRateLimit_activaFallbackAWeatherApi()
  {
    Clima respaldo = new Clima(5.0, 23.0, 8.0);
    when(legacyProveedorClima.obtenerClima(UBICACION))
        .thenThrow(new RateLimitClimaExcedidoException("Rate limit excedido en legacy", null));
    when(weatherApiProveedorClima.obtenerClima(UBICACION)).thenReturn(respaldo);

    ContextoProveedorClima.iniciar("user-2", TipoProveedorClima.LEGACY);
    Clima obtenido = selector.obtenerClima(UBICACION);

    assertThat(obtenido).isEqualTo(respaldo);
    DatosContexto snapshot = ContextoProveedorClima.obtenerSnapshot();
    assertThat(snapshot.fallbackActivo()).isTrue();
    assertThat(snapshot.proveedorUtilizado()).isEqualTo(TipoProveedorClima.WEATHER_API);
  }

  @Test
  @DisplayName("Una vez activado el fallback en el mismo request, las llamadas siguientes van directo a WeatherAPI sin reintentar Legacy")
  void obtenerPronostico_fallbackYaActivoEnContexto_vaDirectoAWeatherApi()
  {
    Clima climaActualRespaldo = new Clima(5.0, 21.0, 9.0);
    Clima pronosticoRespaldo = new Clima(20.0, 22.5, 11.0);
    when(legacyProveedorClima.obtenerClima(UBICACION))
        .thenThrow(new ProveedorClimaIndisponibleException("Legacy caido"));
    when(weatherApiProveedorClima.obtenerClima(UBICACION)).thenReturn(climaActualRespaldo);
    when(weatherApiProveedorClima.obtenerPronostico(UBICACION, FECHA)).thenReturn(pronosticoRespaldo);

    ContextoProveedorClima.iniciar("user-2", TipoProveedorClima.LEGACY);
    assertThat(selector.obtenerClima(UBICACION)).isEqualTo(climaActualRespaldo);
    assertThat(selector.obtenerPronostico(UBICACION, FECHA)).isEqualTo(pronosticoRespaldo);

    verify(legacyProveedorClima, never()).obtenerPronostico(UBICACION, FECHA);
    var statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsLegacy.fallbacksActivados()).isEqualTo(1);
  }

  @Test
  @DisplayName("Si legacy excede su TimeLimiter, el fallback a WeatherAPI recibe el presupuesto restante acotado de la request")
  void obtenerPronostico_legacyExcedeTimeLimiter_fallbackRecibePresupuestoRestanteAcotado()
  {
    LegacyClimaResilience legacyResilience = new LegacyClimaResilience(
        CircuitBreakerRegistry.ofDefaults(),
        RateLimiterRegistry.ofDefaults(),
        RetryRegistry.of(RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(20)).build()),
        TimeLimiterRegistry.of(TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(120))
            .cancelRunningFuture(true)
            .build()),
        monitor,
        10,
        Duration.ofSeconds(1));

    Clima respaldoWeatherApi = new Clima(12.0, 20.0, 8.0);
    AtomicReference<Duration> presupuestoAlLlegarAFallback = new AtomicReference<>();

    when(legacyProveedorClima.obtenerPronostico(UBICACION, FECHA)).thenAnswer(inv ->
        legacyResilience.execute("user-2", () ->
        {
          try
          {
            Thread.sleep(2000);
            return new Clima(0.0, 0.0, 0.0);
          } catch (InterruptedException e)
          {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
          }
        }));
    when(weatherApiProveedorClima.obtenerPronostico(UBICACION, FECHA)).thenAnswer(inv ->
    {
      presupuestoAlLlegarAFallback.set(ContextoProveedorClima.obtenerTiempoRestante());
      return respaldoWeatherApi;
    });

    ContextoProveedorClima.iniciarConPresupuesto("user-2", TipoProveedorClima.LEGACY, Duration.ofMillis(500));
    long inicioNanos = System.nanoTime();
    try
    {
      Clima resultado = selector.obtenerPronostico(UBICACION, FECHA);
      assertThat(resultado).isEqualTo(respaldoWeatherApi);
    } finally
    {
      legacyResilience.cerrarEjecutor();
    }

    long duracionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos);
    assertThat(duracionMs).isLessThan(500L);
    assertThat(presupuestoAlLlegarAFallback.get()).isNotNull();
    assertThat(presupuestoAlLlegarAFallback.get()).isGreaterThan(Duration.ZERO);
    assertThat(presupuestoAlLlegarAFallback.get()).isLessThan(Duration.ofMillis(420));
    assertThat(ContextoProveedorClima.obtenerSnapshot().fallbackActivo()).isTrue();
  }
}
