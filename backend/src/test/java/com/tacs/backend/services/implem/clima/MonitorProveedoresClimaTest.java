package com.tacs.backend.services.implem.clima;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.tacs.backend.config.CacheConfig;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadisticasProveedorClimaDto;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
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
  void calculaCacheHitRatioYRegistraHistorialDeEstadosYTransicionesDelCircuitBreaker()
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

    monitor.registrarExito(TipoProveedorClima.LEGACY);
    monitor.registrarErrorRespuestaInvalida(TipoProveedorClima.LEGACY, "JSON truncado");
    monitor.registrarFallback(TipoProveedorClima.LEGACY);

    CircuitBreaker cbLegacy = cbRegistry.circuitBreaker("legacyClima");
    cbLegacy.transitionToOpenState();

    EstadisticasProveedorClimaDto statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);

    assertThat(statsLegacy.llamadasTotales()).isEqualTo(2);
    assertThat(statsLegacy.llamadasExitosas()).isEqualTo(1);
    assertThat(statsLegacy.erroresTotales()).isEqualTo(1);
    assertThat(statsLegacy.respuestasInvalidas()).isEqualTo(1);
    assertThat(statsLegacy.fallbacksActivados()).isEqualTo(1);
    assertThat(statsLegacy.cacheHits()).isEqualTo(2);
    assertThat(statsLegacy.cacheMisses()).isEqualTo(1);
    assertThat(statsLegacy.cacheHitRatio()).isGreaterThan(66.0);
    assertThat(statsLegacy.estadoCircuitBreaker()).isEqualTo("OPEN");
    assertThat(statsLegacy.estadoActual()).isEqualTo(EstadoProveedorClima.CAIDO);
    assertThat(statsLegacy.historialEstado()).hasSizeGreaterThanOrEqualTo(3);
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
