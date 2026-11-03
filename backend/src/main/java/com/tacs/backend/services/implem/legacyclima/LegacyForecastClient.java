package com.tacs.backend.services.implem.legacyclima;

import com.tacs.backend.domain.clima.Clima;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.tacs.backend.config.CacheConfig.CACHE_LEGACY_FORECAST;

/**
 * Cliente HTTP cacheado para el endpoint {@code POST /rpc} del binario legacy.
 * Normaliza la clave de cache al mismo nivel de granularidad determinista del
 * binario (latitud y longitud redondeadas a 2 decimales, fecha/hora truncada a
 * la hora).
 */
@Slf4j
@Component
public class LegacyForecastClient
{
  private final RestClient restClient;
  private final LegacyClimaResilience resilience;
  private final LegacyClimaValidador validador;
  private final String apiToken;

  public LegacyForecastClient(
      @Qualifier("legacyClimaRestClient") RestClient restClient,
      LegacyClimaResilience resilience,
      LegacyClimaValidador validador,
      @Value("${legacy.clima.api-token:}") String apiToken)
  {
    this.restClient = restClient;
    this.resilience = resilience;
    this.validador = validador;
    this.apiToken = apiToken;
  }

  @Cacheable(
      value = CACHE_LEGACY_FORECAST,
      key = "{#latitudRedondeada, #longitudRedondeada, #fechaHoraTruncada}",
      sync = true)
  public Clima consultarPronosticoCacheado(
      double latitudRedondeada,
      double longitudRedondeada,
      LocalDateTime fechaHoraTruncada)
  {
    String requestId = "req-" + UUID.randomUUID();
    String timestampIso = fechaHoraTruncada.atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_INSTANT);
    LegacyRpcRequest request = LegacyRpcRequest.crearPronostico(
        requestId,
        latitudRedondeada,
        longitudRedondeada,
        timestampIso);

    log.info(
        "CACHE MISS Legacy - invocando POST /rpc (lat={}, lon={}, timestamp={})",
        latitudRedondeada,
        longitudRedondeada,
        timestampIso);

    return resilience.execute(() ->
    {
      String cuerpoCrudo = restClient.post()
          .uri("/rpc")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(String.class);

      return validador.validarYMapea(cuerpoCrudo, requestId);
    });
  }
}
