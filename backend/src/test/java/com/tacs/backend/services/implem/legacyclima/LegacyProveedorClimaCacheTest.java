package com.tacs.backend.services.implem.legacyclima;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tacs.backend.config.CacheConfig;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.services.ProveedorClima;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LegacyProveedorClimaCacheTest
{
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Configuration
  @EnableCaching
  static class TestConfig
  {
    @Bean
    public CacheManager cacheManager()
    {
      CaffeineCacheManager cacheManager = new CaffeineCacheManager(CacheConfig.CACHE_LEGACY_FORECAST);
      cacheManager.setCaffeine(
          Caffeine.newBuilder()
              .expireAfterWrite(60, TimeUnit.SECONDS)
              .recordStats());
      return cacheManager;
    }

    @Bean
    public LegacyForecastClient legacyForecastClient(RestClient legacyClimaRestClient)
    {
      LegacyClimaResilience resilience = new LegacyClimaResilience(
          CircuitBreakerRegistry.ofDefaults(),
          RateLimiterRegistry.ofDefaults(),
          RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
          100,
          Duration.ofSeconds(1));
      return new LegacyForecastClient(legacyClimaRestClient, resilience, new LegacyClimaValidador(), "test-token");
    }

    @Bean
    public LegacyProveedorClima legacyProveedorClima(
        RestClient legacyClimaRestClient,
        LegacyForecastClient legacyForecastClient)
    {
      return new LegacyProveedorClima(legacyClimaRestClient, legacyForecastClient);
    }
  }

  @Test
  void coordenadasCercanasYMinutosDistintosDeLaMismaHoraReutilizanCacheYDisparanUnaSolaLlamadaHttp()
  {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://clima-legacy:9978");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    server.expect(requestTo("http://clima-legacy:9978/rpc"))
        .andRespond(request ->
        {
          MockClientHttpRequest mockReq = (MockClientHttpRequest) request;
          JsonNode body = MAPPER.readTree(mockReq.getBodyAsBytes());
          String reqId = body.get("id").asText();
          String respuesta = """
              {
                "id": "%s",
                "status": "OK",
                "result": {
                  "location": { "lat": -34.58, "lon": -58.43 },
                  "timestamp": "2026-10-28T15:00:00Z",
                  "condition": "clear",
                  "temperature_c": 23.0,
                  "feels_like_c": 23.0,
                  "humidity_pct": 50,
                  "precipitation_probability_pct": 5,
                  "precipitation_mm": 0.0,
                  "wind_kph": 9.0,
                  "wind_direction": "E"
                }
              }
              """.formatted(reqId);
          return withSuccess(respuesta, MediaType.APPLICATION_JSON).createResponse(request);
        });

    try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext())
    {
      ctx.getBeanFactory().registerSingleton("legacyClimaRestClient", restClient);
      ctx.register(TestConfig.class);
      ctx.refresh();

      ProveedorClima proveedor = ctx.getBean(ProveedorClima.class);

      Ubicacion puntoA = new Ubicacion("Palermo", -34.5811, -58.4309);
      Ubicacion puntoB = new Ubicacion("Palermo", -34.5844, -58.4342);
      LocalDateTime hora15y10 = LocalDateTime.of(2026, 10, 28, 15, 10);
      LocalDateTime hora15y50 = LocalDateTime.of(2026, 10, 28, 15, 50);

      Clima primero = proveedor.obtenerPronostico(puntoA, hora15y10);
      Clima segundo = proveedor.obtenerPronostico(puntoB, hora15y50);

      assertThat(primero.getTemperatura()).isEqualTo(23.0);
      assertThat(segundo.getTemperatura()).isEqualTo(23.0);
    }

    server.verify();
  }
}
