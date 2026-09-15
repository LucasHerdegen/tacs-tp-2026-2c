package com.tacs.backend.services.implem.weatherapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tacs.backend.domain.clima.Clima;

import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Subconjunto de campos usados de la respuesta de {@code forecast.json} de
 * WeatherAPI.com. Un solo llamado (con {@code days} acotado a
 * {@code weatherapi.forecast.max-days}) trae el pronostico horario de varios
 * dias; {@link #climaEnHora} resuelve en memoria la hora especifica pedida
 * contra el payload ya traido, sin generar una llamada HTTP nueva por hora.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeatherApiForecastResponse(Forecast forecast)
{
  private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final Duration TOLERANCIA_COINCIDENCIA = Duration.ofMinutes(90);

  /**
   * Busca, entre todas las horas de todos los dias ya traidos, la mas
   * cercana a {@code fechaHorario} y la mapea a {@link Clima}. Si no hay
   * ninguna hora dentro de {@link #TOLERANCIA_COINCIDENCIA}, lanza
   * {@link ProveedorClimaIndisponibleException}.
   */
  public Clima climaEnHora(LocalDateTime fechaHorario)
  {
    return forecast.forecastday().stream()
        .flatMap(dia -> dia.hour().stream())
        .min(Comparator.comparing(hora -> distanciaA(hora, fechaHorario)))
        .filter(hora -> distanciaA(hora, fechaHorario).compareTo(TOLERANCIA_COINCIDENCIA) <= 0)
        .map(Hour::aClima)
        .orElseThrow(() -> new ProveedorClimaIndisponibleException(
            "Sin datos de forecast para " + fechaHorario + " (fuera de la cobertura traida)"));
  }

  private static Duration distanciaA(Hour hora, LocalDateTime fechaHorario)
  {
    return Duration.between(LocalDateTime.parse(hora.time(), FORMATO_HORA), fechaHorario).abs();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Forecast(List<ForecastDay> forecastday) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ForecastDay(String date, List<Hour> hour) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Hour(
      String time,
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
