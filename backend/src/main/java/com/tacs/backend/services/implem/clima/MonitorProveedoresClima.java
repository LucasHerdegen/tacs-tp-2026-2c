package com.tacs.backend.services.implem.clima;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.tacs.backend.config.CacheConfig;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadisticasProveedorClimaDto;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.dtos.admin.PuntoEstadoProveedorDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Centraliza las metricas operativas de cada proveedor de clima (WeatherAPI y
 * Legacy): llamadas totales, exitosas, errores clasificados (respuestas
 * invalidas, timeouts, rechazos de rate limit), fallbacks activados, ratio de
 * aciertos de cache, estado del circuit breaker e historial temporal de salud.
 */
@Component
public class MonitorProveedoresClima
{
  private static final int MAX_HISTORIAL_PUNTOS = 30;

  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final CacheManager cacheManager;
  private final Map<TipoProveedorClima, MetricasProveedor> metricasPorProveedor;

  @Autowired
  public MonitorProveedoresClima(
      CircuitBreakerRegistry circuitBreakerRegistry,
      CacheManager cacheManager)
  {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.cacheManager = cacheManager;
    this.metricasPorProveedor = new EnumMap<>(TipoProveedorClima.class);
    this.metricasPorProveedor.put(
        TipoProveedorClima.WEATHER_API,
        new MetricasProveedor("WeatherAPI.com", "weatherApi", CacheConfig.CACHE_FORECAST_POR_UBICACION));
    this.metricasPorProveedor.put(
        TipoProveedorClima.LEGACY,
        new MetricasProveedor(
            "Humedad, Datos y Pronósticos S.A. (Legacy)",
            "legacyClima",
            CacheConfig.CACHE_LEGACY_FORECAST));
    suscribirTransicionesCircuitBreaker();
  }

  public void registrarExito(TipoProveedorClima proveedor)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.llamadasExitosas.incrementAndGet();
    actualizarEstado(proveedor, EstadoProveedorClima.OK, "Respuesta exitosa del proveedor");
  }

  public void registrarErrorRespuestaInvalida(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.respuestasInvalidas.incrementAndGet();
    actualizarEstadoSegunCircuito(proveedor, EstadoProveedorClima.DEGRADADO, detalle);
  }

  public void registrarErrorTimeout(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.timeouts.incrementAndGet();
    actualizarEstadoSegunCircuito(proveedor, EstadoProveedorClima.DEGRADADO, detalle);
  }

  public void registrarRechazoRateLimit(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.rechazosRateLimit.incrementAndGet();
    actualizarEstadoSegunCircuito(proveedor, EstadoProveedorClima.DEGRADADO, detalle);
  }

  public void registrarErrorGeneral(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    actualizarEstadoSegunCircuito(proveedor, EstadoProveedorClima.CAIDO, detalle);
  }

  public void registrarFallback(TipoProveedorClima proveedorOrigen)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedorOrigen);
    if (metricas != null)
      metricas.fallbacksActivados.incrementAndGet();
  }

  public void registrarEstadoSalud(TipoProveedorClima proveedor, EstadoProveedorClima estado, String detalle)
  {
    actualizarEstado(proveedor, estado, detalle);
  }

  public List<EstadisticasProveedorClimaDto> obtenerEstadisticasProveedores()
  {
    return List.of(
        construirEstadisticas(TipoProveedorClima.WEATHER_API),
        construirEstadisticas(TipoProveedorClima.LEGACY));
  }

  public EstadisticasProveedorClimaDto construirEstadisticas(TipoProveedorClima proveedor)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    CircuitBreaker cb = obtenerCircuitBreaker(metricas.nombreCircuitBreaker);
    String estadoCb = cb != null ? cb.getState().name() : "CLOSED";
    EstadoProveedorClima estadoEfectivo = resolverEstadoEfectivo(metricas, cb);

    DatosCache datosCache = leerEstadisticasCache(metricas.nombreCache);

    return new EstadisticasProveedorClimaDto(
        proveedor,
        metricas.nombreVisible,
        estadoEfectivo,
        estadoCb,
        metricas.llamadasTotales.get(),
        metricas.llamadasExitosas.get(),
        metricas.erroresTotales.get(),
        metricas.respuestasInvalidas.get(),
        metricas.timeouts.get(),
        metricas.rechazosRateLimit.get(),
        metricas.fallbacksActivados.get(),
        datosCache.hits(),
        datosCache.misses(),
        datosCache.hitRatio(),
        new ArrayList<>(metricas.historialEstado));
  }

  private void suscribirTransicionesCircuitBreaker()
  {
    if (circuitBreakerRegistry == null)
      return;
    for (Map.Entry<TipoProveedorClima, MetricasProveedor> entrada : metricasPorProveedor.entrySet())
    {
      TipoProveedorClima proveedor = entrada.getKey();
      MetricasProveedor metricas = entrada.getValue();
      CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(metricas.nombreCircuitBreaker);
      cb.getEventPublisher().onStateTransition(evento ->
      {
        CircuitBreaker.State nuevoEstado = evento.getStateTransition().getToState();
        if (nuevoEstado == CircuitBreaker.State.OPEN || nuevoEstado == CircuitBreaker.State.FORCED_OPEN)
          actualizarEstado(proveedor, EstadoProveedorClima.CAIDO, "Circuit breaker abierto (" + nuevoEstado + ")");
        else if (nuevoEstado == CircuitBreaker.State.HALF_OPEN)
          actualizarEstado(proveedor, EstadoProveedorClima.DEGRADADO, "Circuit breaker en prueba (HALF_OPEN)");
        else if (nuevoEstado == CircuitBreaker.State.CLOSED)
          actualizarEstado(proveedor, EstadoProveedorClima.OK, "Circuit breaker cerrado (CLOSED)");
      });
    }
  }

  private void actualizarEstadoSegunCircuito(
      TipoProveedorClima proveedor,
      EstadoProveedorClima estadoSugerido,
      String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    CircuitBreaker cb = obtenerCircuitBreaker(metricas.nombreCircuitBreaker);
    if (cb != null && (cb.getState() == CircuitBreaker.State.OPEN || cb.getState() == CircuitBreaker.State.FORCED_OPEN))
      actualizarEstado(proveedor, EstadoProveedorClima.CAIDO, "Circuit breaker abierto: " + detalle);
    else
      actualizarEstado(proveedor, estadoSugerido, detalle);
  }

  private void actualizarEstado(TipoProveedorClima proveedor, EstadoProveedorClima nuevoEstado, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    EstadoProveedorClima anterior = metricas.estadoActual.getAndSet(nuevoEstado);
    PuntoEstadoProveedorDto ultimo = metricas.historialEstado.peekLast();
    if (anterior != nuevoEstado || ultimo == null)
    {
      metricas.historialEstado.addLast(new PuntoEstadoProveedorDto(Instant.now(), nuevoEstado, detalle));
      while (metricas.historialEstado.size() > MAX_HISTORIAL_PUNTOS)
        metricas.historialEstado.pollFirst();
    }
  }

  private EstadoProveedorClima resolverEstadoEfectivo(MetricasProveedor metricas, CircuitBreaker cb)
  {
    if (cb != null)
    {
      if (cb.getState() == CircuitBreaker.State.OPEN || cb.getState() == CircuitBreaker.State.FORCED_OPEN)
        return EstadoProveedorClima.CAIDO;
      if (cb.getState() == CircuitBreaker.State.HALF_OPEN)
        return EstadoProveedorClima.DEGRADADO;
    }
    return metricas.estadoActual.get();
  }

  private CircuitBreaker obtenerCircuitBreaker(String nombre)
  {
    if (circuitBreakerRegistry == null)
      return null;
    return circuitBreakerRegistry.circuitBreaker(nombre);
  }

  private DatosCache leerEstadisticasCache(String nombreCache)
  {
    if (cacheManager == null)
      return new DatosCache(0L, 0L, 0.0);
    Cache cache = cacheManager.getCache(nombreCache);
    if (cache instanceof CaffeineCache caffeineCache)
    {
      CacheStats stats = caffeineCache.getNativeCache().stats();
      long hits = stats.hitCount();
      long misses = stats.missCount();
      double ratio = Math.round(stats.hitRate() * 10000.0) / 100.0;
      return new DatosCache(hits, misses, ratio);
    }
    return new DatosCache(0L, 0L, 0.0);
  }

  private record DatosCache(long hits, long misses, double hitRatio)
  {
  }

  private static final class MetricasProveedor
  {
    private final String nombreVisible;
    private final String nombreCircuitBreaker;
    private final String nombreCache;
    private final AtomicLong llamadasTotales = new AtomicLong();
    private final AtomicLong llamadasExitosas = new AtomicLong();
    private final AtomicLong erroresTotales = new AtomicLong();
    private final AtomicLong respuestasInvalidas = new AtomicLong();
    private final AtomicLong timeouts = new AtomicLong();
    private final AtomicLong rechazosRateLimit = new AtomicLong();
    private final AtomicLong fallbacksActivados = new AtomicLong();
    private final AtomicReference<EstadoProveedorClima> estadoActual =
        new AtomicReference<>(EstadoProveedorClima.OK);
    private final ConcurrentLinkedDeque<PuntoEstadoProveedorDto> historialEstado =
        new ConcurrentLinkedDeque<>();

    private MetricasProveedor(String nombreVisible, String nombreCircuitBreaker, String nombreCache)
    {
      this.nombreVisible = nombreVisible;
      this.nombreCircuitBreaker = nombreCircuitBreaker;
      this.nombreCache = nombreCache;
      this.historialEstado.add(new PuntoEstadoProveedorDto(
          Instant.now(),
          EstadoProveedorClima.OK,
          "Estado inicial del proveedor"));
    }
  }
}
