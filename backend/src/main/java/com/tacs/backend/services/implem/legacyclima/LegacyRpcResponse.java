package com.tacs.backend.services.implem.legacyclima;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LegacyRpcResponse(
    String id,
    String status,
    ResultadoRpc result,
    ErrorRpc error)
{
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ResultadoRpc(
      UbicacionResultadoRpc location,
      String timestamp,
      String condition,
      @JsonProperty("temperature_c") Double temperatureC,
      @JsonProperty("feels_like_c") Double feelsLikeC,
      @JsonProperty("humidity_pct") Integer humidityPct,
      @JsonProperty("precipitation_probability_pct") Integer precipitationProbabilityPct,
      @JsonProperty("precipitation_mm") Double precipitationMm,
      @JsonProperty("wind_kph") Double windKph,
      @JsonProperty("wind_direction") String windDirection)
  {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record UbicacionResultadoRpc(
      Double lat,
      Double lon)
  {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ErrorRpc(
      String code,
      String message)
  {
  }
}
