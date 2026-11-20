package com.tacs.backend.services.implem.clima;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.tacs.backend.config.CacheConfig;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadisticasProveedorClimaDto;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.dtos.admin.PuntoEstadoProveedorDto;
import com.tacs.backend.repositories.EstadoPersistidoProveedorClima;
import com.tacs.backend.repositories.IncrementosMetricasProveedor;
import com.tacs.backend.repositories.MetricasProveedorClimaRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Centraliza las metricas operativas de cada proveedor de clima (WeatherAPI y
 * Legacy): llamadas totales, exitosas, errores clasificados (respuestas
 * invalidas, timeouts, rechazos de rate limit), fallbacks activados, ratio de
 * aciertos de cache, estado del circuit breaker e historial temporal de salud.
 * <p>
 * Las operaciones por intento/request actualizan exclusivamente los contadores
 * en memoria sin modificar el estado global ni bloquear la latencia critica,
 * volcando deltas de forma coalescida y atomica en MongoDB. Las transiciones
 * registradas en {@code historialEstado} provienen unicamente de senales reales
 * de salud: sondeo periodico ({@code /healthz} + {@code /readyz}, con
 * histeresis anti-aleteo) y transiciones del Circuit Breaker ({@code CLOSED},
 * {@code HALF_OPEN}, {@code OPEN}).
 */
@Slf4j
@Component
public class MonitorProveedoresClima
{
  static final int MAX_HISTORIAL_PUNTOS = 200;
  static final int HISTERESIS_MUESTRAS_DEFAULT = 2;

  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final CacheManager cacheManager;
  private final MetricasProveedorClimaRepository repositorio;
  private final int histeresisMuestras;
  private final Map<TipoProveedorClima, MetricasProveedor> metricasPorProveedor;
  private final ExecutorService ejecutorPersistencia;
  private final Object lockVolcado = new Object();
  private final AtomicBoolean volcadoProgramado = new AtomicBoolean(false);
  private final AtomicBoolean cerrado = new AtomicBoolean(false);

  public MonitorProveedoresClima(
      CircuitBreakerRegistry circuitBreakerRegistry,
      CacheManager cacheManager)
  {
    this(circuitBreakerRegistry, cacheManager, (MetricasProveedorClimaRepository) null, HISTERESIS_MUESTRAS_DEFAULT);
  }

  public MonitorProveedoresClima(
      CircuitBreakerRegistry circuitBreakerRegistry,
      CacheManager cacheManager,
      int histeresisMuestras)
  {
    this(circuitBreakerRegistry, cacheManager, (MetricasProveedorClimaRepository) null, histeresisMuestras);
  }

  public MonitorProveedoresClima(
      CircuitBreakerRegistry circuitBreakerRegistry,
      CacheManager cacheManager,
      MetricasProveedorClimaRepository repositorio)
  {
    this(circuitBreakerRegistry, cacheManager, repositorio, HISTERESIS_MUESTRAS_DEFAULT);
  }

  @Autowired
  public MonitorProveedoresClima(
      CircuitBreakerRegistry circuitBreakerRegistry,
      CacheManager cacheManager,
      ObjectProvider<MetricasProveedorClimaRepository> repositorioProvider,
      @Value("${clima.salud.histeresis-muestras:2}") int histeresisMuestras)
  {
    this(
        circuitBreakerRegistry,
        cacheManager,
        repositorioProvider != null ? repositorioProvider.getIfAvailable() : null,
        histeresisMuestras);
  }

  public MonitorProveedoresClima(
      CircuitBreakerRegistry circuitBreakerRegistry,
      CacheManager cacheManager,
      MetricasProveedorClimaRepository repositorio,
      int histeresisMuestras)
  {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.cacheManager = cacheManager;
    this.repositorio = repositorio;
    this.histeresisMuestras = Math.max(1, histeresisMuestras);
    this.ejecutorPersistencia = repositorio != null ? crearEjecutorPersistencia() : null;
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
    hidratarDesdeRepositorio();
    suscribirTransicionesCircuitBreaker();
  }

  public void registrarExito(TipoProveedorClima proveedor)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.llamadasExitosas.incrementAndGet();
    metricas.deltaLlamadasTotales.incrementAndGet();
    metricas.deltaLlamadasExitosas.incrementAndGet();
    programarVolcadoContadores();
  }

  public void registrarErrorRespuestaInvalida(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.respuestasInvalidas.incrementAndGet();
    metricas.deltaLlamadasTotales.incrementAndGet();
    metricas.deltaErroresTotales.incrementAndGet();
    metricas.deltaRespuestasInvalidas.incrementAndGet();
    programarVolcadoContadores();
  }

  public void registrarErrorTimeout(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.timeouts.incrementAndGet();
    metricas.deltaLlamadasTotales.incrementAndGet();
    metricas.deltaErroresTotales.incrementAndGet();
    metricas.deltaTimeouts.incrementAndGet();
    programarVolcadoContadores();
  }

  public void registrarRechazoRateLimit(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.rechazosRateLimit.incrementAndGet();
    metricas.deltaLlamadasTotales.incrementAndGet();
    metricas.deltaErroresTotales.incrementAndGet();
    metricas.deltaRechazosRateLimit.incrementAndGet();
    programarVolcadoContadores();
  }

  public void registrarErrorGeneral(TipoProveedorClima proveedor, String detalle)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedor);
    if (metricas == null)
      return;
    metricas.llamadasTotales.incrementAndGet();
    metricas.erroresTotales.incrementAndGet();
    metricas.deltaLlamadasTotales.incrementAndGet();
    metricas.deltaErroresTotales.incrementAndGet();
    programarVolcadoContadores();
  }

  public void registrarFallback(TipoProveedorClima proveedorOrigen)
  {
    MetricasProveedor metricas = metricasPorProveedor.get(proveedorOrigen);
    if (metricas == null)
      return;
    metricas.fallbacksActivados.incrementAndGet();
    metricas.deltaFallbacksActivados.incrementAndGet();
    programarVolcadoContadores();
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

    PuntoEstadoProveedorDto puntoRegistrado;
    EstadoProveedorClima estadoEfectivoFinal;
    EstadoProveedorClima estadoSondeoFinal;

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
      estadoEfectivoFinal = mayorSeveridad(estadoCircuito, metricas.estadoSondeoConfirmado);
      estadoSondeoFinal = metricas.estadoSondeoConfirmado;
      puntoRegistrado = registrarTransicionSiCorresponde(metricas, estadoEfectivoFinal, detalle);
    }

    if (puntoRegistrado != null)
      persistirTransicionEstado(proveedor, estadoEfectivoFinal, estadoSondeoFinal, puntoRegistrado);
    else
      persistirEstadoSondeo(proveedor, estadoEfectivoFinal, estadoSondeoFinal);
  }

  /**
   * Persiste de forma sincronica y atomica cualquier delta de contadores o
   * estadisticas de cache aun pendiente en memoria.
   */
  public void volcarMetricasPendientes()
  {
    if (repositorio == null)
      return;

    synchronized (lockVolcado)
    {
      for (Map.Entry<TipoProveedorClima, MetricasProveedor> entrada : metricasPorProveedor.entrySet())
        volcarProveedor(entrada.getKey(), entrada.getValue());
    }
  }

  @PreDestroy
  public void cerrar()
  {
    cerrado.set(true);
    if (ejecutorPersistencia != null)
    {
      ejecutorPersistencia.shutdown();
      try
      {
        if (!ejecutorPersistencia.awaitTermination(3, TimeUnit.SECONDS))
          ejecutorPersistencia.shutdownNow();
      } catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
        ejecutorPersistencia.shutdownNow();
      }
    }
    volcarMetricasPendientes();
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

    DatosCache datosCache = leerEstadisticasCacheAcumuladas(metricas);

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

  private void hidratarDesdeRepositorio()
  {
    if (repositorio == null)
      return;

    for (Map.Entry<TipoProveedorClima, MetricasProveedor> entrada : metricasPorProveedor.entrySet())
    {
      TipoProveedorClima proveedor = entrada.getKey();
      MetricasProveedor metricas = entrada.getValue();
      try
      {
        Optional<EstadoPersistidoProveedorClima> persistidoOpt = repositorio.buscarPorProveedor(proveedor);
        if (persistidoOpt.isPresent())
        {
          aplicarEstadoPersistido(metricas, persistidoOpt.get());
        }
        else
        {
          PuntoEstadoProveedorDto puntoInicial = metricas.historialEstado.peekFirst();
          repositorio.inicializarSiNoExiste(proveedor, EstadoProveedorClima.OK, puntoInicial);
        }
      } catch (Exception e)
      {
        log.warn("No se pudieron hidratar las metricas persistidas del proveedor {}: {}", proveedor, e.getMessage());
      }
    }
  }

  private void aplicarEstadoPersistido(MetricasProveedor metricas, EstadoPersistidoProveedorClima persistido)
  {
    metricas.llamadasTotales.set(persistido.llamadasTotales());
    metricas.llamadasExitosas.set(persistido.llamadasExitosas());
    metricas.erroresTotales.set(persistido.erroresTotales());
    metricas.respuestasInvalidas.set(persistido.respuestasInvalidas());
    metricas.timeouts.set(persistido.timeouts());
    metricas.rechazosRateLimit.set(persistido.rechazosRateLimit());
    metricas.fallbacksActivados.set(persistido.fallbacksActivados());
    metricas.cacheHitsBase.set(persistido.cacheHits());
    metricas.cacheMissesBase.set(persistido.cacheMisses());

    synchronized (metricas.lockEstado)
    {
      EstadoProveedorClima estadoSondeo = persistido.estadoSondeoConfirmado() != null
          ? persistido.estadoSondeoConfirmado()
          : EstadoProveedorClima.OK;
      EstadoProveedorClima estadoActual = persistido.estadoActual() != null
          ? persistido.estadoActual()
          : estadoSondeo;

      metricas.estadoSondeoConfirmado = estadoSondeo;
      metricas.estadoActual.set(estadoActual);

      List<PuntoEstadoProveedorDto> historialPersistido = persistido.historialEstado();
      if (historialPersistido != null && !historialPersistido.isEmpty())
      {
        metricas.historialEstado.clear();
        int desde = Math.max(0, historialPersistido.size() - MAX_HISTORIAL_PUNTOS);
        for (int i = desde; i < historialPersistido.size(); i++)
          metricas.historialEstado.addLast(historialPersistido.get(i));
      }
    }
  }

  private void programarVolcadoContadores()
  {
    if (ejecutorPersistencia == null || cerrado.get())
      return;
    if (!volcadoProgramado.compareAndSet(false, true))
      return;

    try
    {
      ejecutorPersistencia.execute(() ->
      {
        volcadoProgramado.set(false);
        volcarMetricasPendientes();
      });
    } catch (RejectedExecutionException e)
    {
      volcadoProgramado.set(false);
    }
  }

  private void volcarProveedor(TipoProveedorClima proveedor, MetricasProveedor metricas)
  {
    long deltaLlamadas = metricas.deltaLlamadasTotales.getAndSet(0L);
    long deltaExitosas = metricas.deltaLlamadasExitosas.getAndSet(0L);
    long deltaErrores = metricas.deltaErroresTotales.getAndSet(0L);
    long deltaInvalidas = metricas.deltaRespuestasInvalidas.getAndSet(0L);
    long deltaTimeouts = metricas.deltaTimeouts.getAndSet(0L);
    long deltaRateLimit = metricas.deltaRechazosRateLimit.getAndSet(0L);
    long deltaFallbacks = metricas.deltaFallbacksActivados.getAndSet(0L);

    DatosCache cacheEnVivo = leerEstadisticasCacheEnVivo(metricas.nombresCache);
    long deltaCacheHits = Math.max(0L, cacheEnVivo.hits() - metricas.ultimoCacheHitsVolcado.get());
    long deltaCacheMisses = Math.max(0L, cacheEnVivo.misses() - metricas.ultimoCacheMissesVolcado.get());

    IncrementosMetricasProveedor incrementos = new IncrementosMetricasProveedor(
        deltaLlamadas,
        deltaExitosas,
        deltaErrores,
        deltaInvalidas,
        deltaTimeouts,
        deltaRateLimit,
        deltaFallbacks,
        deltaCacheHits,
        deltaCacheMisses);

    if (incrementos.estaVacio())
      return;

    try
    {
      repositorio.incrementarContadores(proveedor, incrementos);
      metricas.ultimoCacheHitsVolcado.addAndGet(deltaCacheHits);
      metricas.ultimoCacheMissesVolcado.addAndGet(deltaCacheMisses);
    } catch (Exception e)
    {
      metricas.deltaLlamadasTotales.addAndGet(deltaLlamadas);
      metricas.deltaLlamadasExitosas.addAndGet(deltaExitosas);
      metricas.deltaErroresTotales.addAndGet(deltaErrores);
      metricas.deltaRespuestasInvalidas.addAndGet(deltaInvalidas);
      metricas.deltaTimeouts.addAndGet(deltaTimeouts);
      metricas.deltaRechazosRateLimit.addAndGet(deltaRateLimit);
      metricas.deltaFallbacksActivados.addAndGet(deltaFallbacks);
      log.warn("Fallo persistiendo contadores del proveedor {} en MongoDB (se reintentara): {}",
          proveedor, e.getMessage());
    }
  }

  private void persistirTransicionEstado(
      TipoProveedorClima proveedor,
      EstadoProveedorClima estadoActual,
      EstadoProveedorClima estadoSondeo,
      PuntoEstadoProveedorDto punto)
  {
    if (repositorio == null)
      return;
    try
    {
      repositorio.registrarTransicionEstado(proveedor, estadoActual, estadoSondeo, punto, MAX_HISTORIAL_PUNTOS);
    } catch (Exception e)
    {
      log.warn("Fallo persistiendo transicion de estado del proveedor {} en MongoDB: {}", proveedor, e.getMessage());
    }
  }

  private void persistirEstadoSondeo(
      TipoProveedorClima proveedor,
      EstadoProveedorClima estadoActual,
      EstadoProveedorClima estadoSondeo)
  {
    if (repositorio == null)
      return;
    try
    {
      repositorio.actualizarEstadoSondeo(proveedor, estadoActual, estadoSondeo);
    } catch (Exception e)
    {
      log.warn("Fallo actualizando estado de sondeo del proveedor {} en MongoDB: {}", proveedor, e.getMessage());
    }
  }

  private ExecutorService crearEjecutorPersistencia()
  {
    return Executors.newSingleThreadExecutor(runnable ->
    {
      Thread hilo = new Thread(runnable, "monitor-clima-persistencia");
      hilo.setDaemon(true);
      return hilo;
    });
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
    PuntoEstadoProveedorDto puntoRegistrado;
    EstadoProveedorClima estadoEfectivoFinal;
    EstadoProveedorClima estadoSondeoFinal;

    synchronized (metricas.lockEstado)
    {
      estadoEfectivoFinal = mayorSeveridad(estadoCircuito, metricas.estadoSondeoConfirmado);
      estadoSondeoFinal = metricas.estadoSondeoConfirmado;
      String detalleEfectivo = estadoEfectivoFinal == estadoCircuito
          ? detalleCircuito
          : detalleCircuito + " (salud sondeada: " + metricas.estadoSondeoConfirmado + ")";
      puntoRegistrado = registrarTransicionSiCorresponde(metricas, estadoEfectivoFinal, detalleEfectivo);
    }

    if (puntoRegistrado != null)
      persistirTransicionEstado(proveedor, estadoEfectivoFinal, estadoSondeoFinal, puntoRegistrado);
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

  private DatosCache leerEstadisticasCacheAcumuladas(MetricasProveedor metricas)
  {
    DatosCache enVivo = leerEstadisticasCacheEnVivo(metricas.nombresCache);
    long totalHits = metricas.cacheHitsBase.get() + enVivo.hits();
    long totalMisses = metricas.cacheMissesBase.get() + enVivo.misses();
    long totalPedidos = totalHits + totalMisses;
    double ratio = totalPedidos == 0
        ? 0.0
        : Math.round(((double) totalHits / totalPedidos) * 10000.0) / 100.0;
    return new DatosCache(totalHits, totalMisses, ratio);
  }

  private DatosCache leerEstadisticasCacheEnVivo(List<String> nombresCache)
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
    private final AtomicLong cacheHitsBase = new AtomicLong();
    private final AtomicLong cacheMissesBase = new AtomicLong();
    private final AtomicLong deltaLlamadasTotales = new AtomicLong();
    private final AtomicLong deltaLlamadasExitosas = new AtomicLong();
    private final AtomicLong deltaErroresTotales = new AtomicLong();
    private final AtomicLong deltaRespuestasInvalidas = new AtomicLong();
    private final AtomicLong deltaTimeouts = new AtomicLong();
    private final AtomicLong deltaRechazosRateLimit = new AtomicLong();
    private final AtomicLong deltaFallbacksActivados = new AtomicLong();
    private final AtomicLong ultimoCacheHitsVolcado = new AtomicLong();
    private final AtomicLong ultimoCacheMissesVolcado = new AtomicLong();
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
