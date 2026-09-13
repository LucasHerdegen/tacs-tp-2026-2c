package com.tacs.backend.services.implem.weatherapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tacs.backend.domain.clima.Clima;

/**
 * Subconjunto de campos usados de la respuesta de {@code current.json} de
 * WeatherAPI.com. La respuesta real trae muchos mas campos (humedad, uv,
 * presion, etc.); se ignoran los que no se declaran aca.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeatherApiCurrentResponse(Current current)
{
  public Clima aClima()
  {
    return current.aClima();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Current(
      @JsonProperty("temp_c") double tempC,
      @JsonProperty("chance_of_rain") double chanceOfRain,
      @JsonProperty("wind_kph") double windKph)
  {
    Clima aClima()
    {
      return new Clima(chanceOfRain, tempC, windKph);
    }
  }
}
