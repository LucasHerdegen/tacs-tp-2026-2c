package com.tacs.backend.services.implem.weatherapi;

import com.tacs.backend.domain.actividad.Ubicacion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static com.tacs.backend.config.CacheConfig.CACHE_FORECAST_POR_UBICACION;

/**
 * Bean separado de {@link WeatherApiProveedorClima} a proposito: el metodo
 * cacheado tiene que invocarse a traves de la referencia inyectada (el proxy
 * de Spring), no con "this" dentro de la misma clase — una auto-invocacion
 * (this.metodo(...)) nunca pasa por el proxy y @Cacheable queda sin efecto.
 */

@Slf4j
@Component
class WeatherApiForecastClient
{
  private final RestClient restClient;
  private final WeatherApiResilience resilience;
  private final String apiKey;

  WeatherApiForecastClient(
      RestClient restClient,
      WeatherApiResilience resilience,
      @Value("${weatherapi.api-key}") String apiKey)
  {
    this.restClient = restClient;
    this.resilience = resilience;
    this.apiKey = apiKey;
  }

  /**
   * Trae el forecast completo (todos los dias del rango pedido, hora por
   * hora) para una ubicacion. Cacheado por (ubicacion, dias): mientras la
   * entrada siga vigente (TTL de {@code weatherapi.cache.ttl-seconds}), N
   * horas candidatas del mismo dia resuelven contra el mismo resultado
   * cacheado, sin generar una llamada HTTP nueva por cada una.
   */
  @Cacheable(value = CACHE_FORECAST_POR_UBICACION, key = "{#ubicacion, #dias}", sync = true)
  WeatherApiForecastResponse forecastCrudo(Ubicacion ubicacion, int dias)
  {
    log.info("CACHE MISS - pegando a WeatherAPI forecast.json (ubicacion={}, dias={})", ubicacion, dias);

    return resilience.execute(() -> restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/forecast.json")
            .queryParam("key", apiKey)
            .queryParam("q", WeatherApiProveedorClima.consultaUbicacion(ubicacion))
            .queryParam("days", dias)
            .queryParam("aqi", "no")
            .queryParam("alerts", "no")
            .build())
        .retrieve()
        .body(WeatherApiForecastResponse.class));
  }
}
