package com.tacs.backend.services.implem.clima;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.tacs.backend.config.CacheConfig;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadisticasProveedorClimaDto;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.dtos.admin.PuntoEstadoProveedorDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorProveedoresClimaTest
{
  @Test
  @DisplayName("Los errores y exitos por intento contabilizan metricas sin generar transiciones ruidosas en el historial")
  void registrarOperaciones_erroresYExitosPorIntento_contabilizaSinGenerarTransicionesRuidosas()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    CaffeineCacheManager cacheManager = new CaffeineCacheManager(
        CacheConfig.CACHE_FORECAST_POR_UBICACION,
        CacheConfig.CACHE_LEGACY_FORECAST);
    cacheManager.setCaffeine(
        Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .recordStats());

    Cache cacheLegacy = cacheManager.getCache(CacheConfig.CACHE_LEGACY_FORECAST);
    cacheLegacy.get("clave-1", () -> "valor-1");
    cacheLegacy.get("clave-1", () -> "valor-1");
    cacheLegacy.get("clave-1", () -> "valor-1");

    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, cacheManager);

    for (int i = 0; i < 15; i++)
    {
      monitor.registrarErrorRespuestaInvalida(TipoProveedorClima.LEGACY, "JSON truncado");
      monitor.registrarErrorTimeout(TipoProveedorClima.LEGACY, "Read timed out");
      monitor.registrarRechazoRateLimit(TipoProveedorClima.LEGACY, "Rate limit usuario");
      monitor.registrarErrorGeneral(TipoProveedorClima.LEGACY, "503 transitorio");
      monitor.registrarExito(TipoProveedorClima.LEGACY);
      monitor.registrarFallback(TipoProveedorClima.LEGACY);
    }

    EstadisticasProveedorClimaDto statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);

    assertThat(statsLegacy.llamadasTotales()).isEqualTo(75);
    assertThat(statsLegacy.llamadasExitosas()).isEqualTo(15);
    assertThat(statsLegacy.erroresTotales()).isEqualTo(60);
    assertThat(statsLegacy.respuestasInvalidas()).isEqualTo(15);
    assertThat(statsLegacy.timeouts()).isEqualTo(15);
    assertThat(statsLegacy.rechazosRateLimit()).isEqualTo(15);
    assertThat(statsLegacy.fallbacksActivados()).isEqualTo(15);
    assertThat(statsLegacy.cacheHits()).isEqualTo(2);
    assertThat(statsLegacy.cacheMisses()).isEqualTo(1);
    assertThat(statsLegacy.cacheHitRatio()).isGreaterThan(66.0);
    assertThat(statsLegacy.estadoCircuitBreaker()).isEqualTo("CLOSED");
    assertThat(statsLegacy.estadoActual()).isEqualTo(EstadoProveedorClima.OK);
    assertThat(statsLegacy.historialEstado())
        .hasSize(1)
        .extracting(PuntoEstadoProveedorDto::estado)
        .containsExactly(EstadoProveedorClima.OK);
  }

  @Test
  @DisplayName("El sondeo periodico aplica histeresis anti-aleteo y deduplica estados repetidos")
  void registrarEstadoSalud_muestraAisladaNoAletea_confirmaTrasHisteresisYDeduplica()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, null, 2);

    // Una muestra aislada DEGRADADO seguida de OK no debe provocar aleteo.
    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.DEGRADADO, "Sondeo degradado 1");
    assertThat(monitor.construirEstadisticas(TipoProveedorClima.LEGACY).estadoActual())
        .isEqualTo(EstadoProveedorClima.OK);
    assertThat(monitor.construirEstadisticas(TipoProveedorClima.LEGACY).historialEstado()).hasSize(1);

    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.OK, "Sondeo OK");
    assertThat(monitor.construirEstadisticas(TipoProveedorClima.LEGACY).historialEstado()).hasSize(1);

    // Dos muestras consecutivas DEGRADADO confirman la transicion real.
    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.DEGRADADO, "Sondeo degradado 1");
    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.DEGRADADO, "Sondeo degradado 2");
    // Repeticiones consecutivas del mismo estado se deduplican.
    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.DEGRADADO, "Sondeo degradado 3");

    EstadisticasProveedorClimaDto statsDegradado = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsDegradado.estadoActual()).isEqualTo(EstadoProveedorClima.DEGRADADO);
    assertThat(statsDegradado.historialEstado())
        .extracting(PuntoEstadoProveedorDto::estado)
        .containsExactly(EstadoProveedorClima.OK, EstadoProveedorClima.DEGRADADO);

    // Dos muestras consecutivas OK confirman la recuperacion.
    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.OK, "Recuperado 1");
    assertThat(monitor.construirEstadisticas(TipoProveedorClima.LEGACY).estadoActual())
        .isEqualTo(EstadoProveedorClima.DEGRADADO);

    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.OK, "Recuperado 2");
    EstadisticasProveedorClimaDto statsRecuperado = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsRecuperado.estadoActual()).isEqualTo(EstadoProveedorClima.OK);
    assertThat(statsRecuperado.historialEstado())
        .extracting(PuntoEstadoProveedorDto::estado)
        .containsExactly(EstadoProveedorClima.OK, EstadoProveedorClima.DEGRADADO, EstadoProveedorClima.OK);
  }

  @Test
  @DisplayName("Las transiciones del Circuit Breaker se registran de inmediato y prevalecen sobre sondeos menos severos")
  void transicionesCircuitBreaker_openHalfOpenClosed_registraTransicionesRealesYCoordinaConSondeo()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, null, 2);

    CircuitBreaker cbLegacy = cbRegistry.circuitBreaker("legacyClima");
    cbLegacy.transitionToOpenState();

    // Aunque el sondeo reporte DEGRADADO mientras el CB esta OPEN, el estado efectivo permanece CAIDO sin aletear.
    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.DEGRADADO, "Readyz 503");
    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.DEGRADADO, "Readyz 503");
    assertThat(monitor.construirEstadisticas(TipoProveedorClima.LEGACY).estadoActual())
        .isEqualTo(EstadoProveedorClima.CAIDO);
    assertThat(monitor.construirEstadisticas(TipoProveedorClima.LEGACY).historialEstado())
        .extracting(PuntoEstadoProveedorDto::estado)
        .containsExactly(EstadoProveedorClima.OK, EstadoProveedorClima.CAIDO);

    // El sondeo vuelve a OK mientras CB sigue OPEN: no revierte prematuramente a OK.
    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.OK, "Sondeo OK");
    monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.OK, "Sondeo OK");
    assertThat(monitor.construirEstadisticas(TipoProveedorClima.LEGACY).estadoActual())
        .isEqualTo(EstadoProveedorClima.CAIDO);
    assertThat(monitor.construirEstadisticas(TipoProveedorClima.LEGACY).historialEstado()).hasSize(2);

    // Al pasar el CB a HALF_OPEN y luego a CLOSED se registran las transiciones reales.
    cbLegacy.transitionToHalfOpenState();
    cbLegacy.transitionToClosedState();

    EstadisticasProveedorClimaDto statsFinal = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsFinal.estadoCircuitBreaker()).isEqualTo("CLOSED");
    assertThat(statsFinal.estadoActual()).isEqualTo(EstadoProveedorClima.OK);
    assertThat(statsFinal.historialEstado())
        .extracting(PuntoEstadoProveedorDto::estado)
        .containsExactly(
            EstadoProveedorClima.OK,
            EstadoProveedorClima.CAIDO,
            EstadoProveedorClima.DEGRADADO,
            EstadoProveedorClima.OK);
  }

  @Test
  @DisplayName("El historial conserva una ventana acotada de hasta 200 transiciones reales")
  void historialEstado_superaCapacidadMaxima_conservaUltimasDoscientasTransiciones()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, null, 1);

    for (int i = 0; i < 120; i++)
    {
      monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.DEGRADADO, "Degradado " + i);
      monitor.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.OK, "Ok " + i);
    }

    EstadisticasProveedorClimaDto stats = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(stats.historialEstado()).hasSize(MonitorProveedoresClima.MAX_HISTORIAL_PUNTOS);
    assertThat(stats.historialEstado().getLast().detalle()).isEqualTo("Ok 119");
  }

  @Test
  @DisplayName("WeatherAPI contabiliza hits y misses acumulados de cache de forecast y de clima actual (current.json)")
  void construirEstadisticas_weatherApiConAmbasCaches_agregaHitsYMisses()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
    CacheManager cacheManager = new CacheConfig().cacheManager(1800, 300);

    Cache cacheForecast = cacheManager.getCache(CacheConfig.CACHE_FORECAST_POR_UBICACION);
    Cache cacheActual = cacheManager.getCache(CacheConfig.CACHE_CLIMA_ACTUAL_POR_UBICACION);

    cacheForecast.get("palermo-3", () -> "forecast-1");
    cacheForecast.get("palermo-3", () -> "forecast-1");
    cacheActual.get("-34.58,-58.43", () -> "actual-1");
    cacheActual.get("-34.58,-58.43", () -> "actual-1");
    cacheActual.get("-34.58,-58.43", () -> "actual-1");

    MonitorProveedoresClima monitor = new MonitorProveedoresClima(cbRegistry, cacheManager);
    EstadisticasProveedorClimaDto statsWeatherApi = monitor.construirEstadisticas(TipoProveedorClima.WEATHER_API);

    assertThat(statsWeatherApi.cacheHits()).isEqualTo(3);
    assertThat(statsWeatherApi.cacheMisses()).isEqualTo(2);
    assertThat(statsWeatherApi.cacheHitRatio()).isEqualTo(60.0);
  }
}
