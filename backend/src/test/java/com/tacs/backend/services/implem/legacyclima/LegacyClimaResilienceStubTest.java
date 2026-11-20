package com.tacs.backend.services.implem.legacyclima;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.tacs.backend.config.CacheConfig;
import com.tacs.backend.config.LegacyClimaConfig;
import com.tacs.backend.config.TimeConfig;
import com.tacs.backend.config.WeatherApiConfig;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadisticasProveedorClimaDto;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima.DatosContexto;
import com.tacs.backend.services.implem.clima.MonitorProveedoresClima;
import com.tacs.backend.services.implem.clima.SelectorProveedorClima;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot.ratelimiter.autoconfigure.RateLimiterAutoConfiguration;
import io.github.resilience4j.springboot.retry.autoconfigure.RetryAutoConfiguration;
import io.github.resilience4j.springboot.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class LegacyClimaResilienceStubTest
{
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final double TEMPERATURA_LEGACY = 19.0;
  private static final double LLUVIA_LEGACY = 35.0;
  private static final double VIENTO_LEGACY = 14.0;
  private static final double TEMPERATURA_WEATHER_API = 22.5;
  private static final double LLUVIA_WEATHER_API = 15.0;
  private static final double VIENTO_WEATHER_API = 11.0;

  private TimeZone timeZoneOriginal;
  private CountDownLatch liberarCuelgues;
  private HttpServer servidorLegacy;
  private ExecutorService ejecutorLegacy;
  private final AtomicReference<HttpHandler> manejadorLegacy = new AtomicReference<>();
  private HttpServer servidorWeatherApi;
  private ExecutorService ejecutorWeatherApi;
  private final AtomicReference<HttpHandler> manejadorWeatherApi = new AtomicReference<>();
  private final AtomicInteger llamadasWeatherApiCurrent = new AtomicInteger();
  private final AtomicInteger llamadasWeatherApiForecast = new AtomicInteger();

  private ConfigurableApplicationContext contexto;
  private SelectorProveedorClima selector;
  private LegacyProveedorClima legacyProveedorClima;
  private MonitorProveedoresClima monitor;
  private CircuitBreakerRegistry circuitBreakerRegistry;
  private RetryRegistry retryRegistry;
  private TimeLimiterRegistry timeLimiterRegistry;
  private long readTimeoutLegacyMs;
  private long timeoutTotalMs;

  @Configuration(proxyBeanMethods = false)
  @EnableCaching
  @ImportAutoConfiguration(classes = {
      ConfigurationPropertiesAutoConfiguration.class,
      PropertyPlaceholderAutoConfiguration.class,
      JacksonAutoConfiguration.class,
      HttpMessageConvertersAutoConfiguration.class,
      ImperativeHttpClientAutoConfiguration.class,
      RestClientAutoConfiguration.class,
      CircuitBreakerAutoConfiguration.class,
      RateLimiterAutoConfiguration.class,
      RetryAutoConfiguration.class,
      TimeLimiterAutoConfiguration.class
  })
  @Import({
      LegacyClimaConfig.class,
      WeatherApiConfig.class,
      CacheConfig.class,
      TimeConfig.class
  })
  @ComponentScan(
      basePackages = {
          "com.tacs.backend.services.implem.clima",
          "com.tacs.backend.services.implem.legacyclima",
          "com.tacs.backend.services.implem.weatherapi"
      },
      excludeFilters = {
          @Filter(type = FilterType.REGEX, pattern = ".*Test.*"),
          @Filter(type = FilterType.ANNOTATION, classes = Configuration.class)
      })
  static class ContextoResilienciaRealConfig
  {
  }

  @BeforeEach
  void configurarEntornoYServidoresStub() throws IOException
  {
    timeZoneOriginal = TimeZone.getDefault();
    liberarCuelgues = new CountDownLatch(1);
    llamadasWeatherApiCurrent.set(0);
    llamadasWeatherApiForecast.set(0);

    manejadorLegacy.set(exchange -> responderTexto(exchange, 503, "text/plain", "unresponsive\n"));
    manejadorWeatherApi.set(this::responderWeatherApiValido);

    servidorLegacy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ejecutorLegacy = Executors.newCachedThreadPool();
    servidorLegacy.setExecutor(ejecutorLegacy);
    servidorLegacy.createContext("/rpc", exchange -> manejadorLegacy.get().handle(exchange));
    servidorLegacy.start();

    servidorWeatherApi = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ejecutorWeatherApi = Executors.newCachedThreadPool();
    servidorWeatherApi.setExecutor(ejecutorWeatherApi);
    servidorWeatherApi.createContext("/current.json", exchange ->
    {
      llamadasWeatherApiCurrent.incrementAndGet();
      manejadorWeatherApi.get().handle(exchange);
    });
    servidorWeatherApi.createContext("/forecast.json", exchange ->
    {
      llamadasWeatherApiForecast.incrementAndGet();
      manejadorWeatherApi.get().handle(exchange);
    });
    servidorWeatherApi.start();

    int puertoLegacy = servidorLegacy.getAddress().getPort();
    int puertoWeatherApi = servidorWeatherApi.getAddress().getPort();

    Map<String, Object> overrides = Map.of(
        "legacy.clima.base-url", "http://127.0.0.1:" + puertoLegacy,
        "legacy.clima.api-token", "token-stub-legacy",
        "weatherapi.base-url", "http://127.0.0.1:" + puertoWeatherApi,
        "weatherapi.api-key", "api-key-stub-weather");

    contexto = new SpringApplicationBuilder(ContextoResilienciaRealConfig.class)
        .web(WebApplicationType.NONE)
        .bannerMode(Banner.Mode.OFF)
        .initializers(ctx -> ctx.getEnvironment().getPropertySources()
            .addFirst(new MapPropertySource("stubOverrides", overrides)))
        .run();

    selector = contexto.getBean(SelectorProveedorClima.class);
    legacyProveedorClima = contexto.getBean(LegacyProveedorClima.class);
    monitor = contexto.getBean(MonitorProveedoresClima.class);
    circuitBreakerRegistry = contexto.getBean(CircuitBreakerRegistry.class);
    retryRegistry = contexto.getBean(RetryRegistry.class);
    timeLimiterRegistry = contexto.getBean(TimeLimiterRegistry.class);

    Environment environment = contexto.getEnvironment();
    readTimeoutLegacyMs = environment.getProperty("legacy.clima.read-timeout-ms", Long.class, 0L);
    timeoutTotalMs = environment.getProperty("clima.operacion.timeout-total-ms", Long.class, 0L);
  }

  @AfterEach
  void limpiarRecursosYServidores()
  {
    ContextoProveedorClima.limpiar();
    if (liberarCuelgues != null)
      liberarCuelgues.countDown();
    if (contexto != null)
    {
      contexto.close();
      contexto = null;
    }
    detenerServidorLegacySiActivo();
    if (servidorWeatherApi != null)
    {
      servidorWeatherApi.stop(0);
      servidorWeatherApi = null;
    }
    if (ejecutorWeatherApi != null)
    {
      ejecutorWeatherApi.shutdownNow();
      ejecutorWeatherApi = null;
    }
    TimeZone.setDefault(timeZoneOriginal);
  }

  private void detenerServidorLegacySiActivo()
  {
    if (servidorLegacy != null)
    {
      servidorLegacy.stop(0);
      servidorLegacy = null;
    }
    if (ejecutorLegacy != null)
    {
      ejecutorLegacy.shutdownNow();
      ejecutorLegacy = null;
    }
  }

  private void responderWeatherApiValido(HttpExchange exchange) throws IOException
  {
    exchange.getRequestBody().readAllBytes();
    String ruta = exchange.getRequestURI().getPath();
    if (ruta.endsWith("/current.json"))
    {
      String jsonActual = String.format(
          Locale.ROOT,
          """
              {
                "current": {
                  "temp_c": %.1f,
                  "chance_of_rain": %.1f,
                  "wind_kph": %.1f
                }
              }
              """,
          TEMPERATURA_WEATHER_API,
          LLUVIA_WEATHER_API,
          VIENTO_WEATHER_API);
      responderTexto(exchange, 200, "application/json", jsonActual);
      return;
    }

    LocalDate hoy = LocalDate.now();
    StringBuilder diasJson = new StringBuilder();
    for (int d = 0; d < 3; d++)
    {
      if (d > 0)
        diasJson.append(',');
      LocalDate dia = hoy.plusDays(d);
      StringBuilder horasJson = new StringBuilder();
      for (int h = 0; h < 24; h++)
      {
        if (h > 0)
          horasJson.append(',');
        horasJson.append(String.format(
            Locale.ROOT,
            """
                {"time":"%s %02d:00","temp_c":%.1f,"chance_of_rain":%.1f,"wind_kph":%.1f}""",
            dia,
            h,
            TEMPERATURA_WEATHER_API,
            LLUVIA_WEATHER_API,
            VIENTO_WEATHER_API));
      }
      diasJson.append("""
          {"date":"%s","hour":[%s]}""".formatted(dia, horasJson));
    }

    String jsonForecast = """
        {"forecast":{"forecastday":[%s]}}""".formatted(diasJson);
    responderTexto(exchange, 200, "application/json", jsonForecast);
  }

  private String leerCuerpoYLuegoResponderRpcValido(HttpExchange exchange) throws IOException
  {
    byte[] cuerpo = exchange.getRequestBody().readAllBytes();
    responderRpcValido(exchange, cuerpo);
    return new String(cuerpo, StandardCharsets.UTF_8);
  }

  private void responderRpcValido(HttpExchange exchange, byte[] cuerpoSolicitud) throws IOException
  {
    JsonNode nodo = MAPPER.readTree(cuerpoSolicitud);
    String reqId = nodo.get("id").asText();
    double lat = nodo.at("/params/location/lat").asDouble();
    double lon = nodo.at("/params/location/lon").asDouble();
    String timestamp = nodo.at("/params/timestamp").asText();

    String respuesta = String.format(
        Locale.ROOT,
        """
            {
              "id": "%s",
              "status": "OK",
              "result": {
                "location": { "lat": %.2f, "lon": %.2f },
                "timestamp": "%s",
                "condition": "partly cloudy",
                "temperature_c": %.1f,
                "feels_like_c": %.1f,
                "humidity_pct": 60,
                "precipitation_probability_pct": %d,
                "precipitation_mm": 0.8,
                "wind_kph": %.1f,
                "wind_direction": "SE"
              }
            }
            """,
        reqId,
        lat,
        lon,
        timestamp,
        TEMPERATURA_LEGACY,
        TEMPERATURA_LEGACY,
        (int) LLUVIA_LEGACY,
        VIENTO_LEGACY);

    responderTexto(exchange, 200, "application/json", respuesta);
  }

  private void colgarHastaCierre(HttpExchange exchange) throws IOException
  {
    exchange.getRequestBody().readAllBytes();
    try
    {
      liberarCuelgues.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
    } finally
    {
      exchange.close();
    }
  }

  private void responderTexto(HttpExchange exchange, int codigo, String contentType, String cuerpo)
  {
    try
    {
      byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", contentType);
      exchange.sendResponseHeaders(codigo, bytes.length);
      try (OutputStream out = exchange.getResponseBody())
      {
        out.write(bytes);
      }
    } catch (IOException ignored)
    {
    } finally
    {
      exchange.close();
    }
  }

  @Test
  @DisplayName("Timeout real (read-timeout): el stub demora mas que read-timeout, RestClient corta realmente y recupera en el reintento")
  void obtenerPronostico_stubLegacyDemoraMasQueReadTimeoutEnPrimerIntento_cortaPorSocketTimeoutYRecuperaEnReintento()
  {
    assertThat(readTimeoutLegacyMs).isEqualTo(350L);

    AtomicInteger intentosLegacy = new AtomicInteger();
    manejadorLegacy.set(exchange ->
    {
      int intento = intentosLegacy.incrementAndGet();
      if (intento == 1)
      {
        colgarHastaCierre(exchange);
        return;
      }
      leerCuerpoYLuegoResponderRpcValido(exchange);
    });

    Ubicacion ubicacion = new Ubicacion("Palermo", -34.58, -58.43);
    LocalDateTime fecha = LocalDate.now().plusDays(1).atTime(15, 0);

    ContextoProveedorClima.iniciarConPresupuesto(
        "user-timeout-1",
        TipoProveedorClima.LEGACY,
        Duration.ofMillis(timeoutTotalMs));

    long inicioNanos = System.nanoTime();
    Clima clima = selector.obtenerPronostico(ubicacion, fecha);
    long duracionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos);

    assertThat(clima.getTemperatura()).isEqualTo(TEMPERATURA_LEGACY);
    assertThat(clima.getProbabilidadLluvia()).isEqualTo(LLUVIA_LEGACY);
    assertThat(clima.getViento()).isEqualTo(VIENTO_LEGACY);
    assertThat(ContextoProveedorClima.obtenerSnapshot().fallbackActivo()).isFalse();
    assertThat(ContextoProveedorClima.obtenerSnapshot().proveedorUtilizado()).isEqualTo(TipoProveedorClima.LEGACY);
    assertThat(intentosLegacy.get()).isEqualTo(2);
    assertThat(llamadasWeatherApiForecast.get()).isEqualTo(0);
    assertThat(duracionMs).isGreaterThanOrEqualTo(300L).isLessThan(1250L);

    EstadisticasProveedorClimaDto statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsLegacy.timeouts()).isEqualTo(1);
    assertThat(statsLegacy.llamadasExitosas()).isEqualTo(1);

    CircuitBreaker cbLegacy = circuitBreakerRegistry.circuitBreaker("legacyClima");
    assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cbLegacy.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    assertThat(cbLegacy.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
  }

  @Test
  @DisplayName("Timeout real (read-timeout): si todos los intentos cuelgan mas que read-timeout, dispara reintentos y activa fallback a WeatherAPI")
  void obtenerPronostico_stubLegacyCuelgaEnTodosLosIntentos_cortaPorReadTimeoutReintentaYActivaFallback()
  {
    AtomicInteger intentosLegacy = new AtomicInteger();
    manejadorLegacy.set(exchange ->
    {
      intentosLegacy.incrementAndGet();
      colgarHastaCierre(exchange);
    });

    Ubicacion ubicacion = new Ubicacion("Belgrano", -34.56, -58.46);
    LocalDateTime fecha = LocalDate.now().plusDays(1).atTime(16, 0);

    ContextoProveedorClima.iniciarConPresupuesto(
        "user-timeout",
        TipoProveedorClima.LEGACY,
        Duration.ofMillis(timeoutTotalMs));

    long inicioNanos = System.nanoTime();
    Clima clima = selector.obtenerPronostico(ubicacion, fecha);
    long duracionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos);

    assertThat(clima.getTemperatura()).isEqualTo(TEMPERATURA_WEATHER_API);
    assertThat(clima.getProbabilidadLluvia()).isEqualTo(LLUVIA_WEATHER_API);
    assertThat(clima.getViento()).isEqualTo(VIENTO_WEATHER_API);

    DatosContexto snapshot = ContextoProveedorClima.obtenerSnapshot();
    assertThat(snapshot.fallbackActivo()).isTrue();
    assertThat(snapshot.proveedorUtilizado()).isEqualTo(TipoProveedorClima.WEATHER_API);
    assertThat(intentosLegacy.get()).isBetween(2, 3);
    assertThat(llamadasWeatherApiForecast.get()).isEqualTo(1);
    assertThat(duracionMs).isGreaterThanOrEqualTo(300L).isLessThan(timeoutTotalMs);

    EstadisticasProveedorClimaDto statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsLegacy.timeouts()).isGreaterThanOrEqualTo(2);
    assertThat(statsLegacy.fallbacksActivados()).isEqualTo(1);

    CircuitBreaker cbLegacy = circuitBreakerRegistry.circuitBreaker("legacyClima");
    assertThat(cbLegacy.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("Limite de tiempo total: con ambos stubs colgados, obtenerClima + obtenerPronostico quedan acotados al presupuesto real")
  void obtenerClimaYPronostico_ambosStubsCuelgan_tiempoTotalQuedaAcotadoAlPresupuestoRealConfigurado()
  {
    assertThat(timeoutTotalMs).isEqualTo(2500L);
    assertThat(timeLimiterRegistry.timeLimiter("legacyClima").getTimeLimiterConfig().getTimeoutDuration())
        .isEqualTo(Duration.ofMillis(1300));
    assertThat(timeLimiterRegistry.timeLimiter("weatherApi").getTimeLimiterConfig().getTimeoutDuration())
        .isEqualTo(Duration.ofMillis(1000));

    manejadorLegacy.set(this::colgarHastaCierre);
    manejadorWeatherApi.set(this::colgarHastaCierre);

    Ubicacion ubicacion = new Ubicacion("Recoleta", -34.59, -58.39);
    LocalDateTime fecha = LocalDate.now().plusDays(1).atTime(18, 0);

    ContextoProveedorClima.iniciarConPresupuesto(
        "user-presupuesto",
        TipoProveedorClima.LEGACY,
        Duration.ofMillis(timeoutTotalMs));

    long inicioTotalNanos = System.nanoTime();

    assertThatThrownBy(() -> selector.obtenerClima(ubicacion))
        .isInstanceOf(ProveedorClimaIndisponibleException.class)
        .hasCauseInstanceOf(TimeoutException.class);

    long inicioSegundaNanos = System.nanoTime();
    assertThatThrownBy(() -> selector.obtenerPronostico(ubicacion, fecha))
        .isInstanceOf(ProveedorClimaIndisponibleException.class)
        .hasCauseInstanceOf(TimeoutException.class);
    long duracionSegundaMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioSegundaNanos);
    long duracionTotalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioTotalNanos);

    assertThat(duracionTotalMs).isGreaterThanOrEqualTo(2000L).isLessThan(timeoutTotalMs + 700L);
    assertThat(duracionSegundaMs).isLessThan(700L);

    Duration tiempoRestante = ContextoProveedorClima.obtenerTiempoRestante();
    assertThat(tiempoRestante).isNotNull();
    assertThat(tiempoRestante).isLessThanOrEqualTo(Duration.ofMillis(50));
  }

  @Test
  @DisplayName("CB con config real: ~40% de fallas por intento (JSON corrupto + hangs) recuperadas en Retry mantienen el circuito CLOSED")
  void circuitBreaker_cuarentaPorCientoFallasPorIntentoRecuperadasEnRetry_permaneceCerradoConConfigReal()
  {
    CircuitBreaker cbLegacy = circuitBreakerRegistry.circuitBreaker("legacyClima");
    assertThat(cbLegacy.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(10);
    assertThat(cbLegacy.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(5);
    assertThat(cbLegacy.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);

    Retry retryLegacy = retryRegistry.retry("legacyClima");
    assertThat(retryLegacy.getRetryConfig().getMaxAttempts()).isEqualTo(3);

    AtomicInteger intentoGlobal = new AtomicInteger();
    manejadorLegacy.set(exchange ->
    {
      int intento = intentoGlobal.incrementAndGet();
      switch (intento)
      {
        case 1, 11 ->
        {
          exchange.getRequestBody().readAllBytes();
          responderTexto(exchange, 200, "text/html", "<html><body><h1>502 Bad Gateway</h1></body></html>");
        }
        case 3 ->
        {
          exchange.getRequestBody().readAllBytes();
          responderTexto(exchange, 200, "application/json", "{\"id\":\"req-1\",\"status\":\"OK\",\"result\":{\"temperature_c\":20.");
        }
        case 5 ->
        {
          exchange.getRequestBody().readAllBytes();
          responderTexto(exchange, 200, "application/json", "{\"id\":\"req-1\",\"status\":\"OK\",\"result\":{\"location\":{\"lat\":-34.60,\"lon\"=-58.38}}}");
        }
        case 7 ->
        {
          exchange.getRequestBody().readAllBytes();
          String respuestaIdDistinto = """
              {
                "id": "id-incorrecto",
                "status": "OK",
                "result": {
                  "location": { "lat": -34.60, "lon": -58.38 },
                  "timestamp": "2026-10-28T15:00:00Z",
                  "temperature_c": 20.0,
                  "precipitation_probability_pct": 10,
                  "wind_kph": 10.0
                }
              }
              """;
          responderTexto(exchange, 200, "application/json", respuestaIdDistinto);
        }
        case 9, 12 -> colgarHastaCierre(exchange);
        default -> leerCuerpoYLuegoResponderRpcValido(exchange);
      }
    });

    Ubicacion ubicacion = new Ubicacion("Caballito", -34.62, -58.44);
    LocalDateTime fechaBase = LocalDate.now().plusDays(1).atTime(8, 0);

    for (int i = 0; i < 10; i++)
    {
      ContextoProveedorClima.iniciar("usuario-sano-" + i, TipoProveedorClima.LEGACY);
      Clima clima = selector.obtenerPronostico(ubicacion, fechaBase.plusHours(i));
      assertThat(clima.getTemperatura()).isEqualTo(TEMPERATURA_LEGACY);
      assertThat(ContextoProveedorClima.obtenerSnapshot().fallbackActivo()).isFalse();
      assertThat(ContextoProveedorClima.obtenerSnapshot().proveedorUtilizado()).isEqualTo(TipoProveedorClima.LEGACY);
    }

    assertThat(intentoGlobal.get()).isEqualTo(17);
    assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cbLegacy.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(10);
    assertThat(cbLegacy.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);

    EstadisticasProveedorClimaDto statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsLegacy.respuestasInvalidas()).isEqualTo(5);
    assertThat(statsLegacy.timeouts()).isEqualTo(2);
    assertThat(statsLegacy.fallbacksActivados()).isEqualTo(0);
  }

  @Test
  @DisplayName("CB con config real: respuestas 401 Unauthorized NO se reintentan y NO abren el Circuit Breaker")
  void circuitBreaker_respuestas401Unauthorized_noReintentaYNoAbreCircuitoConConfigReal()
  {
    AtomicInteger intentosStubLegacy = new AtomicInteger();
    manejadorLegacy.set(exchange ->
    {
      intentosStubLegacy.incrementAndGet();
      exchange.getRequestBody().readAllBytes();
      String json401 = """
          {"status":"ERROR","error":{"code":"UNAUTHORIZED","message":"invalid token"}}""";
      responderTexto(exchange, 401, "application/json", json401);
    });

    Ubicacion ubicacion = new Ubicacion("Almagro", -34.61, -58.42);
    LocalDateTime fechaBase = LocalDate.now().plusDays(1).atTime(9, 0);

    for (int i = 0; i < 7; i++)
    {
      int indice = i;
      ContextoProveedorClima.iniciar("usuario-401-" + indice, TipoProveedorClima.LEGACY);
      assertThatThrownBy(() -> legacyProveedorClima.obtenerPronostico(ubicacion, fechaBase.plusHours(indice)))
          .isInstanceOf(ProveedorClimaIndisponibleException.class)
          .hasCauseInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    CircuitBreaker cbLegacy = circuitBreakerRegistry.circuitBreaker("legacyClima");
    assertThat(intentosStubLegacy.get()).isEqualTo(7);
    assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cbLegacy.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
    assertThat(cbLegacy.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(0);
  }

  @Test
  @DisplayName("CB con config real: 503 sostenido abre exactamente al alcanzar minimum-number-of-calls (5) y corta rapido al fallback")
  void circuitBreaker_503SostenidoUnresponsive_abreExactamenteEnUmbralRealYCortaRapidoAlFallback()
  {
    AtomicInteger intentosStubLegacy = new AtomicInteger();
    manejadorLegacy.set(exchange ->
    {
      intentosStubLegacy.incrementAndGet();
      exchange.getRequestBody().readAllBytes();
      responderTexto(exchange, 503, "text/plain", "unresponsive\n");
    });

    CircuitBreaker cbLegacy = circuitBreakerRegistry.circuitBreaker("legacyClima");
    Ubicacion ubicacion = new Ubicacion("San Telmo", -34.62, -58.37);
    LocalDateTime fechaBase = LocalDate.now().plusDays(1).atTime(10, 0);

    for (int i = 1; i <= 4; i++)
    {
      ContextoProveedorClima.iniciar("usuario-503-" + i, TipoProveedorClima.LEGACY);
      Clima clima = selector.obtenerPronostico(ubicacion, fechaBase.plusHours(i));
      assertThat(clima.getTemperatura()).isEqualTo(TEMPERATURA_WEATHER_API);
      assertThat(ContextoProveedorClima.obtenerSnapshot().fallbackActivo()).isTrue();
      assertThat(ContextoProveedorClima.obtenerSnapshot().proveedorUtilizado())
          .isEqualTo(TipoProveedorClima.WEATHER_API);
      assertThat(intentosStubLegacy.get()).isEqualTo(i * 3);
      assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    ContextoProveedorClima.iniciar("usuario-503-5", TipoProveedorClima.LEGACY);
    Clima climaQuinto = selector.obtenerPronostico(ubicacion, fechaBase.plusHours(5));
    assertThat(climaQuinto.getTemperatura()).isEqualTo(TEMPERATURA_WEATHER_API);
    assertThat(intentosStubLegacy.get()).isEqualTo(15);
    assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThat(cbLegacy.getMetrics().getNumberOfFailedCalls()).isEqualTo(5);

    long inicioRapidoNanos = System.nanoTime();
    for (int i = 6; i <= 8; i++)
    {
      ContextoProveedorClima.iniciar("usuario-503-" + i, TipoProveedorClima.LEGACY);
      Clima clima = selector.obtenerPronostico(ubicacion, fechaBase.plusHours(i));
      assertThat(clima.getTemperatura()).isEqualTo(TEMPERATURA_WEATHER_API);
      assertThat(ContextoProveedorClima.obtenerSnapshot().fallbackActivo()).isTrue();
    }
    long duracionRapidaMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioRapidoNanos);

    assertThat(duracionRapidaMs).isLessThan(500L);
    assertThat(intentosStubLegacy.get()).isEqualTo(15);
    assertThat(cbLegacy.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(3);

    EstadisticasProveedorClimaDto statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsLegacy.estadoCircuitBreaker()).isEqualTo("OPEN");
    assertThat(statsLegacy.fallbacksActivados()).isEqualTo(8);
  }

  @Test
  @DisplayName("CB con config real: 3 exitos + 2 fallas (40% < 50%) mantienen CLOSED y la 3ra falla (50%) abre exactamente")
  void circuitBreaker_tasaDeFallasAlcanzaExactamenteCincuentaPorCiento_abreAlCumplirUmbralReal()
  {
    AtomicInteger intentosStubLegacy = new AtomicInteger();
    AtomicInteger modoFalla = new AtomicInteger(0);
    manejadorLegacy.set(exchange ->
    {
      intentosStubLegacy.incrementAndGet();
      if (modoFalla.get() == 1)
      {
        exchange.getRequestBody().readAllBytes();
        responderTexto(exchange, 503, "text/plain", "unresponsive\n");
        return;
      }
      leerCuerpoYLuegoResponderRpcValido(exchange);
    });

    CircuitBreaker cbLegacy = circuitBreakerRegistry.circuitBreaker("legacyClima");
    Ubicacion ubicacion = new Ubicacion("Villa Urquiza", -34.57, -58.49);
    LocalDateTime fechaBase = LocalDate.now().plusDays(1).atTime(6, 0);

    for (int i = 0; i < 3; i++)
    {
      ContextoProveedorClima.iniciar("usuario-umbral-" + i, TipoProveedorClima.LEGACY);
      Clima clima = selector.obtenerPronostico(ubicacion, fechaBase.plusHours(i));
      assertThat(clima.getTemperatura()).isEqualTo(TEMPERATURA_LEGACY);
    }
    assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cbLegacy.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(3);

    modoFalla.set(1);
    for (int i = 3; i < 5; i++)
    {
      ContextoProveedorClima.iniciar("usuario-umbral-" + i, TipoProveedorClima.LEGACY);
      Clima clima = selector.obtenerPronostico(ubicacion, fechaBase.plusHours(i));
      assertThat(clima.getTemperatura()).isEqualTo(TEMPERATURA_WEATHER_API);
    }
    assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(cbLegacy.getMetrics().getFailureRate()).isEqualTo(40.0f);

    ContextoProveedorClima.iniciar("usuario-umbral-5", TipoProveedorClima.LEGACY);
    Clima climaSexto = selector.obtenerPronostico(ubicacion, fechaBase.plusHours(5));
    assertThat(climaSexto.getTemperatura()).isEqualTo(TEMPERATURA_WEATHER_API);
    assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThat(cbLegacy.getMetrics().getFailureRate()).isEqualTo(50.0f);

    int intentosAntesDelCorte = intentosStubLegacy.get();
    ContextoProveedorClima.iniciar("usuario-umbral-6", TipoProveedorClima.LEGACY);
    Clima climaSeptimo = selector.obtenerPronostico(ubicacion, fechaBase.plusHours(6));
    assertThat(climaSeptimo.getTemperatura()).isEqualTo(TEMPERATURA_WEATHER_API);
    assertThat(ContextoProveedorClima.obtenerSnapshot().fallbackActivo()).isTrue();
    assertThat(intentosStubLegacy.get()).isEqualTo(intentosAntesDelCorte);
  }

  @Test
  @DisplayName("Fallback bajo carga concurrente: legacy con fallas + usuario abusivo aisla rate limit por usuario sin cascada de 503")
  void fallbackBajoCargaConcurrente_legacyConFallasYUsuarioAbusivo_aislaRateLimitPorUsuarioSinCascada503()
      throws Exception
  {
    ConcurrentHashMap<String, AtomicInteger> intentosPorRequestId = new ConcurrentHashMap<>();
    AtomicInteger secuenciaRequests = new AtomicInteger();
    ConcurrentHashMap<String, Boolean> debeFallarPrimerIntento = new ConcurrentHashMap<>();

    manejadorLegacy.set(exchange ->
    {
      byte[] cuerpo = exchange.getRequestBody().readAllBytes();
      JsonNode nodo = MAPPER.readTree(cuerpo);
      String reqId = nodo.get("id").asText();
      int intento = intentosPorRequestId.computeIfAbsent(reqId, id -> new AtomicInteger()).incrementAndGet();
      boolean fallaPrimera = debeFallarPrimerIntento.computeIfAbsent(
          reqId,
          id -> secuenciaRequests.incrementAndGet() % 3 != 0);

      if (intento == 1 && fallaPrimera)
      {
        responderTexto(exchange, 200, "application/json", "{\"id\":\"" + reqId + "\",\"status\":\"OK\",}");
        return;
      }
      responderRpcValido(exchange, cuerpo);
    });

    List<String> usuarios = List.of(
        "usuario-abusivo", "usuario-abusivo", "usuario-abusivo", "usuario-abusivo", "usuario-abusivo",
        "usuario-abusivo", "usuario-abusivo", "usuario-abusivo", "usuario-abusivo", "usuario-abusivo",
        "usuario-normal-1", "usuario-normal-1",
        "usuario-normal-2", "usuario-normal-2",
        "usuario-normal-3", "usuario-normal-3");

    LocalDateTime fechaBase = LocalDate.now().plusDays(1).atTime(0, 0);
    CountDownLatch inicioSimultaneo = new CountDownLatch(1);
    List<Future<ResultadoConcurrente>> futuros = new ArrayList<>();

    ExecutorService pool = Executors.newFixedThreadPool(usuarios.size());
    try
    {
      for (int i = 0; i < usuarios.size(); i++)
      {
        int indice = i;
        String usuarioId = usuarios.get(i);
        Ubicacion ubicacion = new Ubicacion("Zona-" + indice, -34.50 - (indice * 0.02), -58.30 - (indice * 0.02));
        LocalDateTime fecha = fechaBase.plusHours(indice);

        Callable<ResultadoConcurrente> tarea = () ->
        {
          inicioSimultaneo.await();
          ContextoProveedorClima.iniciarConPresupuesto(
              usuarioId,
              TipoProveedorClima.LEGACY,
              Duration.ofMillis(timeoutTotalMs));
          try
          {
            Clima clima = selector.obtenerPronostico(ubicacion, fecha);
            return new ResultadoConcurrente(usuarioId, clima, null, ContextoProveedorClima.obtenerSnapshot());
          } finally
          {
            ContextoProveedorClima.limpiar();
          }
        };
        futuros.add(pool.submit(tarea));
      }

      inicioSimultaneo.countDown();
      List<ResultadoConcurrente> resultados = new ArrayList<>();
      for (Future<ResultadoConcurrente> futuro : futuros)
        resultados.add(futuro.get(10, TimeUnit.SECONDS));

      assertThat(resultados).hasSize(16);
      for (ResultadoConcurrente resultado : resultados)
        assertThat(resultado.climaPronostico()).isNotNull();

      List<ResultadoConcurrente> deAbusivo = resultados.stream()
          .filter(r -> "usuario-abusivo".equals(r.usuarioId()))
          .toList();
      long abusivoEnLegacy = deAbusivo.stream()
          .filter(r -> !r.snapshot().fallbackActivo())
          .count();
      long abusivoEnFallback = deAbusivo.stream()
          .filter(r -> r.snapshot().fallbackActivo())
          .count();
      assertThat(abusivoEnLegacy).isLessThanOrEqualTo(5L);
      assertThat(abusivoEnFallback).isGreaterThanOrEqualTo(5L);

      List<ResultadoConcurrente> deNormales = resultados.stream()
          .filter(r -> !"usuario-abusivo".equals(r.usuarioId()))
          .toList();
      assertThat(deNormales).hasSize(6);
      assertThat(deNormales).allSatisfy(resultado ->
      {
        assertThat(resultado.snapshot().fallbackActivo()).isFalse();
        assertThat(resultado.snapshot().proveedorUtilizado()).isEqualTo(TipoProveedorClima.LEGACY);
        assertThat(resultado.climaPronostico().getTemperatura()).isEqualTo(TEMPERATURA_LEGACY);
      });
    } finally
    {
      pool.shutdownNow();
    }

    CircuitBreaker cbLegacy = circuitBreakerRegistry.circuitBreaker("legacyClima");
    assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

    EstadisticasProveedorClimaDto statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    EstadisticasProveedorClimaDto statsWeather = monitor.construirEstadisticas(TipoProveedorClima.WEATHER_API);
    assertThat(statsLegacy.rechazosRateLimit()).isGreaterThanOrEqualTo(5L);
    assertThat(statsWeather.erroresTotales()).isEqualTo(0L);
  }

  @Test
  @DisplayName("Fallback bajo carga concurrente: legacy caido con 18 usuarios abre el CB y WeatherAPI cacheado absorbe sin 503")
  void fallbackBajoCargaConcurrente_legacyCaidoConMultiplesUsuarios_abreCircuitoYAbsorbeCargaSinCascada503()
      throws Exception
  {
    detenerServidorLegacySiActivo();

    int totalUsuarios = 18;
    int cantidadUbicaciones = 6;
    LocalDateTime fechaBase = LocalDate.now().plusDays(1).atTime(12, 0);
    CountDownLatch inicioSimultaneo = new CountDownLatch(1);
    List<Future<ResultadoConcurrente>> futuros = new ArrayList<>();

    ExecutorService pool = Executors.newFixedThreadPool(totalUsuarios);
    try
    {
      for (int i = 0; i < totalUsuarios; i++)
      {
        int indiceUbicacion = i % cantidadUbicaciones;
        String usuarioId = "usuario-carga-" + i;
        Ubicacion ubicacion = new Ubicacion(
            "Barrio-" + indiceUbicacion,
            -34.55 - (indiceUbicacion * 0.02),
            -58.40 - (indiceUbicacion * 0.02));
        LocalDateTime fecha = fechaBase.plusHours(indiceUbicacion);

        Callable<ResultadoConcurrente> tarea = () ->
        {
          inicioSimultaneo.await();
          ContextoProveedorClima.iniciarConPresupuesto(
              usuarioId,
              TipoProveedorClima.LEGACY,
              Duration.ofMillis(timeoutTotalMs));
          try
          {
            Clima climaActual = selector.obtenerClima(ubicacion);
            Clima pronostico = selector.obtenerPronostico(ubicacion, fecha);
            return new ResultadoConcurrente(
                usuarioId,
                pronostico,
                climaActual,
                ContextoProveedorClima.obtenerSnapshot());
          } finally
          {
            ContextoProveedorClima.limpiar();
          }
        };
        futuros.add(pool.submit(tarea));
      }

      inicioSimultaneo.countDown();
      List<ResultadoConcurrente> resultados = new ArrayList<>();
      for (Future<ResultadoConcurrente> futuro : futuros)
        resultados.add(futuro.get(15, TimeUnit.SECONDS));

      assertThat(resultados).hasSize(totalUsuarios);
      assertThat(resultados).allSatisfy(resultado ->
      {
        assertThat(resultado.climaActual()).isNotNull();
        assertThat(resultado.climaActual().getTemperatura()).isEqualTo(TEMPERATURA_WEATHER_API);
        assertThat(resultado.climaPronostico()).isNotNull();
        assertThat(resultado.climaPronostico().getTemperatura()).isEqualTo(TEMPERATURA_WEATHER_API);
        assertThat(resultado.climaPronostico().getProbabilidadLluvia()).isEqualTo(LLUVIA_WEATHER_API);
        assertThat(resultado.climaPronostico().getViento()).isEqualTo(VIENTO_WEATHER_API);
        assertThat(resultado.snapshot().proveedorSeleccionado()).isEqualTo(TipoProveedorClima.LEGACY);
        assertThat(resultado.snapshot().proveedorUtilizado()).isEqualTo(TipoProveedorClima.WEATHER_API);
        assertThat(resultado.snapshot().fallbackActivo()).isTrue();
      });
    } finally
    {
      pool.shutdownNow();
    }

    CircuitBreaker cbLegacy = circuitBreakerRegistry.circuitBreaker("legacyClima");
    assertThat(cbLegacy.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThat(llamadasWeatherApiCurrent.get()).isLessThanOrEqualTo(cantidadUbicaciones);
    assertThat(llamadasWeatherApiForecast.get()).isLessThanOrEqualTo(cantidadUbicaciones);

    EstadisticasProveedorClimaDto statsWeather = monitor.construirEstadisticas(TipoProveedorClima.WEATHER_API);
    EstadisticasProveedorClimaDto statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsWeather.rechazosRateLimit()).isEqualTo(0L);
    assertThat(statsWeather.erroresTotales()).isEqualTo(0L);
    assertThat(statsLegacy.fallbacksActivados()).isEqualTo(totalUsuarios);
  }

  private record ResultadoConcurrente(
      String usuarioId,
      Clima climaPronostico,
      Clima climaActual,
      DatosContexto snapshot)
  {
  }
}
