package com.tacs.backend.services.implem.clima;

import com.tacs.backend.config.CacheConfig;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadisticasProveedorClimaDto;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.dtos.admin.PuntoEstadoProveedorDto;
import com.tacs.backend.persistence.mappers.MetricasProveedorClimaMapper;
import com.tacs.backend.persistence.repositories.MetricasProveedorClimaMongoRepository;
import com.tacs.backend.persistence.repositories.MetricasProveedorClimaRepositoryImpl;
import com.tacs.backend.repositories.MetricasProveedorClimaRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest(properties = {
    "spring.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/tacs_test}",
    "spring.data.mongodb.auto-index-creation=true"
})
@Import({
    MetricasProveedorClimaRepositoryImpl.class,
    MetricasProveedorClimaMapper.class
})
class MonitorProveedoresClimaPersistenciaIntegrationTest
{
  @Autowired
  private MetricasProveedorClimaRepository repositorio;

  @Autowired
  private MetricasProveedorClimaMongoRepository mongoRepository;

  @MockitoBean
  private CacheManager cacheManagerContexto;

  @BeforeEach
  void setUp()
  {
    mongoRepository.deleteAll();
  }

  @AfterEach
  void tearDown()
  {
    mongoRepository.deleteAll();
  }

  @Test
  @DisplayName("Al recrear MonitorProveedoresClima se hidratan desde MongoDB contadores, cache stats, estado e historial sin perder datos")
  void reiniciarMonitor_metricasEHistorialPersistidos_hidrataExactamenteTrasReinicio()
  {
    CacheManager cacheInicial = new CacheConfig().cacheManager(1800, 300);
    Cache cacheLegacyInicial = cacheInicial.getCache(CacheConfig.CACHE_LEGACY_FORECAST);
    cacheLegacyInicial.get("caba-1", () -> "clima-1");
    cacheLegacyInicial.get("caba-1", () -> "clima-1");
    cacheLegacyInicial.get("caba-1", () -> "clima-1");

    CircuitBreakerRegistry cbInicial = CircuitBreakerRegistry.ofDefaults();
    MonitorProveedoresClima monitorAntesReinicio = new MonitorProveedoresClima(
        cbInicial,
        cacheInicial,
        repositorio,
        2);

    monitorAntesReinicio.registrarExito(TipoProveedorClima.LEGACY);
    monitorAntesReinicio.registrarExito(TipoProveedorClima.LEGACY);
    monitorAntesReinicio.registrarErrorRespuestaInvalida(TipoProveedorClima.LEGACY, "JSON corrupto");
    monitorAntesReinicio.registrarErrorTimeout(TipoProveedorClima.LEGACY, "Timeout 350ms");
    monitorAntesReinicio.registrarRechazoRateLimit(TipoProveedorClima.LEGACY, "Rate limit usuario");
    monitorAntesReinicio.registrarErrorGeneral(TipoProveedorClima.LEGACY, "503");
    monitorAntesReinicio.registrarFallback(TipoProveedorClima.LEGACY);

    // Transiciones reales: sondeo confirmado DEGRADADO (2 muestras) + apertura del CB (CAIDO)
    monitorAntesReinicio.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.DEGRADADO, "Readyz 503");
    monitorAntesReinicio.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.DEGRADADO, "Readyz 503");
    CircuitBreaker cbLegacy = cbInicial.circuitBreaker("legacyClima");
    cbLegacy.transitionToOpenState();

    // Tambien registramos metricas en WEATHER_API para verificar independencia por proveedor
    monitorAntesReinicio.registrarExito(TipoProveedorClima.WEATHER_API);

    // Simulamos apagado ordenado (@PreDestroy)
    monitorAntesReinicio.cerrar();

    List<PuntoEstadoProveedorDto> historialAntes = monitorAntesReinicio
        .construirEstadisticas(TipoProveedorClima.LEGACY)
        .historialEstado();

    // Simulamos un reinicio completo: nuevas instancias vacias de CacheManager, CircuitBreakerRegistry y Monitor
    CacheManager cacheNuevo = new CacheConfig().cacheManager(1800, 300);
    CircuitBreakerRegistry cbNuevo = CircuitBreakerRegistry.ofDefaults();
    MonitorProveedoresClima monitorDespuesReinicio = new MonitorProveedoresClima(
        cbNuevo,
        cacheNuevo,
        repositorio,
        2);

    try
    {
      EstadisticasProveedorClimaDto statsLegacy = monitorDespuesReinicio.construirEstadisticas(TipoProveedorClima.LEGACY);
      assertThat(statsLegacy.llamadasTotales()).isEqualTo(6);
      assertThat(statsLegacy.llamadasExitosas()).isEqualTo(2);
      assertThat(statsLegacy.erroresTotales()).isEqualTo(4);
      assertThat(statsLegacy.respuestasInvalidas()).isEqualTo(1);
      assertThat(statsLegacy.timeouts()).isEqualTo(1);
      assertThat(statsLegacy.rechazosRateLimit()).isEqualTo(1);
      assertThat(statsLegacy.fallbacksActivados()).isEqualTo(1);
      assertThat(statsLegacy.cacheHits()).isEqualTo(2);
      assertThat(statsLegacy.cacheMisses()).isEqualTo(1);
      assertThat(statsLegacy.cacheHitRatio()).isGreaterThan(66.0);
      // El estado sondeado persistido era DEGRADADO (CB nuevo inicia CLOSED), por lo que el estado efectivo sigue DEGRADADO
      assertThat(statsLegacy.estadoActual()).isEqualTo(EstadoProveedorClima.DEGRADADO);
      assertThat(statsLegacy.historialEstado())
          .hasSize(historialAntes.size())
          .extracting(PuntoEstadoProveedorDto::estado)
          .containsExactly(EstadoProveedorClima.OK, EstadoProveedorClima.DEGRADADO, EstadoProveedorClima.CAIDO);
      assertThat(statsLegacy.historialEstado().get(1).detalle()).isEqualTo("Readyz 503");

      // Nuevas operaciones despues del reinicio acumulan sobre lo hidratado sin duplicar el punto inicial
      Cache cacheLegacyNuevo = cacheNuevo.getCache(CacheConfig.CACHE_LEGACY_FORECAST);
      cacheLegacyNuevo.get("caba-2", () -> "clima-2");
      cacheLegacyNuevo.get("caba-2", () -> "clima-2");
      monitorDespuesReinicio.registrarExito(TipoProveedorClima.LEGACY);
      monitorDespuesReinicio.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.OK, "Sondeo OK");
      monitorDespuesReinicio.registrarEstadoSalud(TipoProveedorClima.LEGACY, EstadoProveedorClima.OK, "Sondeo OK");
      monitorDespuesReinicio.cerrar();

      MonitorProveedoresClima tercerArranque = new MonitorProveedoresClima(
          CircuitBreakerRegistry.ofDefaults(),
          new CacheConfig().cacheManager(1800, 300),
          repositorio,
          2);
      try
      {
        EstadisticasProveedorClimaDto statsTercero = tercerArranque.construirEstadisticas(TipoProveedorClima.LEGACY);
        assertThat(statsTercero.llamadasTotales()).isEqualTo(7);
        assertThat(statsTercero.llamadasExitosas()).isEqualTo(3);
        assertThat(statsTercero.cacheHits()).isEqualTo(3);
        assertThat(statsTercero.cacheMisses()).isEqualTo(2);
        assertThat(statsTercero.estadoActual()).isEqualTo(EstadoProveedorClima.OK);
        assertThat(statsTercero.historialEstado())
            .extracting(PuntoEstadoProveedorDto::estado)
            .containsExactly(
                EstadoProveedorClima.OK,
                EstadoProveedorClima.DEGRADADO,
                EstadoProveedorClima.CAIDO,
                EstadoProveedorClima.OK);

        EstadisticasProveedorClimaDto statsWeather = tercerArranque.construirEstadisticas(TipoProveedorClima.WEATHER_API);
        assertThat(statsWeather.llamadasTotales()).isEqualTo(1);
        assertThat(statsWeather.llamadasExitosas()).isEqualTo(1);
        assertThat(statsWeather.historialEstado()).hasSize(1);
      } finally
      {
        tercerArranque.cerrar();
      }
    } finally
    {
      monitorDespuesReinicio.cerrar();
    }
  }

  @Test
  @DisplayName("Las actualizaciones concurrentes de contadores persisten de forma atomica sin perder incrementos")
  void registrarContadoresConcurrentes_multiplesHilos_persisteTotalExactoEnMongo() throws Exception
  {
    MonitorProveedoresClima monitor = new MonitorProveedoresClima(
        CircuitBreakerRegistry.ofDefaults(),
        null,
        repositorio,
        2);

    int hilos = 8;
    int operacionesPorHilo = 25;
    CountDownLatch inicio = new CountDownLatch(1);
    CountDownLatch fin = new CountDownLatch(hilos);
    ExecutorService pool = Executors.newFixedThreadPool(hilos);

    try
    {
      for (int h = 0; h < hilos; h++)
      {
        pool.submit(() ->
        {
          try
          {
            inicio.await();
            for (int i = 0; i < operacionesPorHilo; i++)
            {
              monitor.registrarExito(TipoProveedorClima.LEGACY);
              monitor.registrarErrorTimeout(TipoProveedorClima.LEGACY, "Timeout");
              monitor.registrarFallback(TipoProveedorClima.LEGACY);
            }
          } catch (InterruptedException e)
          {
            Thread.currentThread().interrupt();
          } finally
          {
            fin.countDown();
          }
        });
      }

      inicio.countDown();
      assertThat(fin.await(10, TimeUnit.SECONDS)).isTrue();
    } finally
    {
      pool.shutdownNow();
      monitor.cerrar();
    }

    MonitorProveedoresClima monitorHidratado = new MonitorProveedoresClima(
        CircuitBreakerRegistry.ofDefaults(),
        null,
        repositorio,
        2);
    try
    {
      EstadisticasProveedorClimaDto stats = monitorHidratado.construirEstadisticas(TipoProveedorClima.LEGACY);
      long esperado = (long) hilos * operacionesPorHilo;
      assertThat(stats.llamadasTotales()).isEqualTo(esperado * 2);
      assertThat(stats.llamadasExitosas()).isEqualTo(esperado);
      assertThat(stats.erroresTotales()).isEqualTo(esperado);
      assertThat(stats.timeouts()).isEqualTo(esperado);
      assertThat(stats.fallbacksActivados()).isEqualTo(esperado);
    } finally
    {
      monitorHidratado.cerrar();
    }
  }
}
