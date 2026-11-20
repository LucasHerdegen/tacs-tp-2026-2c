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
import org.springframework.beans.factory.annotation.Value;
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
 * <p>
 * Las operaciones por intento/request actualizan exclusivamente los contadores
 * sin modificar el estado global del proveedor. Las transiciones registradas en
 * {@code historialEstado} provienen unicamente de senales reales de salud:
 * sondeo periodico ({@code /healthz} + {@code /readyz}, con histeresis para
 * evitar aleteo) y transiciones del Circuit Breaker ({@code CLOSED},
 * {@code HALF_OPEN}, {@code OPEN}).
 */
@Component
public class MonitorProveedoresClima
{
  static final int MAX_HISTORIAL_PUNTOS = 200;
  static final int HISTERESIS_MUESTRAS_DEFAULT = 2;

  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final CacheManager cacheManager;
  private final int histeresisMuestras;
  private final Map<TipoProveedorClima, MetricasProveedor> metricasPorProveedor;

  public MonitorProveedoresClima(
      CircuitBreakerRegistry circuitBreakerRegistry,
      CacheManager cacheManager)
  {
    this(circuitBreakerRegistry, cacheManager, HISTERESIS_MUESTRAS_DEFAULT);
  }

  @Autowired
  public MonitorProveedoresClima(
      CircuitBreakerRegistry circuitBreakerRegistry,
      CacheManager cacheManager,
      @Value("${clima.salud.histeresis-muestras:2}") int histeresisMuestras)
  {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.cacheManager = cacheManager;
    this.histeresisMuestras = Math.max(1, histeresisMuestras);
    this.metricasPorProveedor = new EnumMap<>(TipoProveedorClima.class);
    this.metricasPorProveedor.put(
        TipoProveedorClima.WEATHER_API,
        new MetricasProveedor(
            "WeatherAPI.com",
            "weatherApi",
            List.of(
                CacheConfig.CACHE_FORECAST_POR_UBICACION,
                CacheConfig.CACHE_CLIMA_ACTUAL_POR_UBICACION)));
    this.metricasPorProveedor.put(
        TipoProveedorClima.LEGACY,
        new MetricasProveedor(
            "Humedad, Datos y Pronósticos S.A. (Legacy)",
            "legacyClima",
            List.of(CacheConfig.CACHE_LEGACY_FORECAST)));
    suscribirTransicionesCircuitBreaker();
  }

  public void registrarExito(TipoProveedorClima proveedor)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.llamadasExitosas.incrementAndGet();
  }

  public void registrarErrorRespuestaInvalida(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.respuestasInvalidas.incrementAndGet();
  }

  public void registrarErrorTimeout(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.timeouts.incrementAndGet();
  }

  public void registrarRechazoRateLimit(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.rechazosRateLimit.incrementAndGet();
  }

  public void registrarErrorGeneral(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
  }

  public void registrarFallback(TipoProveedorClima proveedorOrigen)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedorOrigen);
    if (metricas != null)
      metricas.fallbacksActivados.incrementAndGet();
  }

  /**
   * Procesa el resultado de un sondeo periodico de salud ({@code /healthz} y
   * {@code /readyz}) aplicando histeresis para absorber fallas aisladas o
   * reinicios breves, y deduplicacion frente al estado efectivo combinado con
   * el Circuit Breaker.
   */
  public void registrarEstadoSalud(TipoProveedorClima proveedor, EstadoProveedorClima estadoSondeo, String detalle)
  {
    if (proveedor == null || estadoSondeo == null)
      return;
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;

    synchronized (metricas.lockEstado)
    {
      if (estadoSondeo == metricas.estadoSondeoConfirmado)
      {
        metricas.estadoSondeoPendiente = null;
        metricas.muestrasPendientes = 0;
        return;
      }

      if (estadoSondeo == metricas.estadoSondeoPendiente)
      {
        metricas.muestrasPendientes++;
      }
      else
      {
        metricas.estadoSondeoPendiente = estadoSondeo;
        metricas.muestrasPendientes = 1;
      }

      if (metricas.muestrasPendientes < histeresisMuestras)
        return;

      metricas.estadoSondeoConfirmado = estadoSondeo;
      metricas.estadoSondeoPendiente = null;
      metricas.muestrasPendientes = 0;

      EstadoProveedorClima estadoCircuito = estadoDesdeCircuitBreaker(obtenerCircuitBreaker(metricas.nombreCircuitBreaker));
      EstadoProveedorClima nuevoEfectivo = mayorSeveridad(estadoCircuito, metricas.estadoSondeoConfirmado);
      registrarTransicionSiCorresponde(metricas, nuevoEfectivo, detalle);
    }
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

    DatosCache datosCache = leerEstadisticasCache(metricas.nombresCache);

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
          manejarTransicionCircuitBreaker(proveedor, evento.getStateTransition().getToState()));
    }
  }

  private void manejarTransicionCircuitBreaker(TipoProveedorClima proveedor, CircuitBreaker.State nuevoEstadoCb)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;

    EstadoProveedorClima estadoCircuito = mapearEstadoCircuitBreaker(nuevoEstadoCb);
    if (estadoCircuito == null)
      return;

    String detalleCircuito = describirEstadoCircuitBreaker(nuevoEstadoCb);

    synchronized (metricas.lockEstado)
    {
      EstadoProveedorClima nuevoEfectivo = mayorSeveridad(estadoCircuito, metricas.estadoSondeoConfirmado);
      String detalleEfectivo = nuevoEfectivo == estadoCircuito
          ? detalleCircuito
          : detalleCircuito + " (salud sondeada: " + metricas.estadoSondeoConfirmado + ")";
      registrarTransicionSiCorresponde(metricas, nuevoEfectivo, detalleEfectivo);
    }
  }

  private PuntoEstadoProveedorDto registrarTransicionSiCorresponde(
      MetricasProveedor metricas,
      EstadoProveedorClima nuevoEstado,
      String detalle)
  {
    EstadoProveedorClima anterior = metricas.estadoActual.getAndSet(nuevoEstado);
    PuntoEstadoProveedorDto ultimo = metricas.historialEstado.peekLast();
    if (anterior != nuevoEstado || ultimo == null)
    {
      PuntoEstadoProveedorDto punto = new PuntoEstadoProveedorDto(Instant.now(), nuevoEstado, detalle);
      metricas.historialEstado.addLast(punto);
      while (metricas.historialEstado.size() > MAX_HISTORIAL_PUNTOS)
        metricas.historialEstado.pollFirst();
      return punto;
    }
    return null;
  }

  private EstadoProveedorClima resolverEstadoEfectivo(MetricasProveedor metricas, CircuitBreaker cb)
  {
    EstadoProveedorClima estadoCircuito = estadoDesdeCircuitBreaker(cb);
    EstadoProveedorClima estadoSondeo;
    synchronized (metricas.lockEstado)
    {
      estadoSondeo = metricas.estadoSondeoConfirmado;
    }
    return mayorSeveridad(estadoCircuito, estadoSondeo);
  }

  private EstadoProveedorClima estadoDesdeCircuitBreaker(CircuitBreaker cb)
  {
    if (cb == null)
      return EstadoProveedorClima.OK;
    EstadoProveedorClima mapeado = mapearEstadoCircuitBreaker(cb.getState());
    return mapeado != null ? mapeado : EstadoProveedorClima.OK;
  }

  private EstadoProveedorClima mapearEstadoCircuitBreaker(CircuitBreaker.State estadoCb)
  {
    if (estadoCb == CircuitBreaker.State.OPEN || estadoCb == CircuitBreaker.State.FORCED_OPEN)
      return EstadoProveedorClima.CAIDO;
    if (estadoCb == CircuitBreaker.State.HALF_OPEN)
      return EstadoProveedorClima.DEGRADADO;
    if (estadoCb == CircuitBreaker.State.CLOSED)
      return EstadoProveedorClima.OK;
    return null;
  }

  private String describirEstadoCircuitBreaker(CircuitBreaker.State estadoCb)
  {
    if (estadoCb == CircuitBreaker.State.OPEN || estadoCb == CircuitBreaker.State.FORCED_OPEN)
      return "Circuit breaker abierto (" + estadoCb + ")";
    if (estadoCb == CircuitBreaker.State.HALF_OPEN)
      return "Circuit breaker en prueba (HALF_OPEN)";
    return "Circuit breaker cerrado (CLOSED)";
  }

  private EstadoProveedorClima mayorSeveridad(EstadoProveedorClima primero, EstadoProveedorClima segundo)
  {
    return severidad(primero) >= severidad(segundo) ? primero : segundo;
  }

  private int severidad(EstadoProveedorClima estado)
  {
    if (estado == EstadoProveedorClima.CAIDO)
      return 2;
    if (estado == EstadoProveedorClima.DEGRADADO)
      return 1;
    return 0;
  }

  private CircuitBreaker obtenerCircuitBreaker(String nombre)
  {
    if (circuitBreakerRegistry == null)
      return null;
    return circuitBreakerRegistry.circuitBreaker(nombre);
  }

  private DatosCache leerEstadisticasCache(List<String> nombresCache)
  {
    if (cacheManager == null || nombresCache == null || nombresCache.isEmpty())
      return new DatosCache(0L, 0L, 0.0);
    long totalHits = 0L;
    long totalMisses = 0L;
    for (String nombreCache : nombresCache)
    {
      Cache cache = cacheManager.getCache(nombreCache);
      if (cache instanceof CaffeineCache caffeineCache)
      {
        CacheStats stats = caffeineCache.getNativeCache().stats();
        totalHits += stats.hitCount();
        totalMisses += stats.missCount();
      }
    }
    long totalPedidos = totalHits + totalMisses;
    double ratio = totalPedidos == 0
        ? 0.0
        : Math.round(((double) totalHits / totalPedidos) * 10000.0) / 100.0;
    return new DatosCache(totalHits, totalMisses, ratio);
  }

  private record DatosCache(long hits, long misses, double hitRatio)
  {
  }

  private static final class MetricasProveedor
  {
    private final String nombreVisible;
    private final String nombreCircuitBreaker;
    private final List<String> nombresCache;
    private final Object lockEstado = new Object();
    private final AtomicLong llamadasTotales = new AtomicLong();
    private final AtomicLong llamadasExitosas = new AtomicLong();
    private final AtomicLong erroresTotales = new AtomicLong();
    private final AtomicLong respuestasInvalidas = new AtomicLong();
    private final AtomicLong timeouts = new AtomicLong();
    private final AtomicLong rechazosRateLimit = new AtomicLong();
    private final AtomicLong fallbacksActivados = new AtomicLong();
    private final AtomicReference<EstadoProveedorClima> estadoActual =
        new AtomicReference<>(EstadoProveedorClima.OK);
    private EstadoProveedorClima estadoSondeoConfirmado = EstadoProveedorClima.OK;
    private EstadoProveedorClima estadoSondeoPendiente;
    private int muestrasPendientes;
    private final ConcurrentLinkedDeque<PuntoEstadoProveedorDto> historialEstado =
        new ConcurrentLinkedDeque<>();

    private MetricasProveedor(String nombreVisible, String nombreCircuitBreaker, List<String> nombresCache)
    {
      this.nombreVisible = nombreVisible;
      this.nombreCircuitBreaker = nombreCircuitBreaker;
      this.nombresCache = nombresCache;
      this.historialEstado.add(new PuntoEstadoProveedorDto(
          Instant.now(),
          EstadoProveedorClima.OK,
          "Estado inicial del proveedor"));
    }
  }
}
