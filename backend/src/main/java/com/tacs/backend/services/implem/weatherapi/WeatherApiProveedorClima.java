package com.tacs.backend.services.implem.weatherapi;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.services.ProveedorClima;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Implementacion real de {@link ProveedorClima} contra WeatherAPI.com. 
 * Batching: {@link #obtenerPronostico} nunca genera una llamada
 * HTTP por hora candidata, siempre pide (y cachea) el forecast completo de
 * los dias necesarios para una ubicacion, y resuelve la hora especifica en
 * memoria contra el payload ya traido ({@link WeatherApiForecastResponse#climaEnHora}).
 */
@Slf4j
@Service
public class WeatherApiProveedorClima implements ProveedorClima
{
  private final RestClient restClient;
  private final WeatherApiForecastClient forecastClient;
  private final WeatherApiResilience resilience;
  private final String apiKey;
  private final int maxDias;

  public WeatherApiProveedorClima(
      RestClient restClient,
      WeatherApiForecastClient forecastClient,
      WeatherApiResilience resilience,
      @Value("${weatherapi.api-key}") String apiKey,
      @Value("${weatherapi.forecast.max-days}") int maxDias)
  {
    this.restClient = restClient;
    this.forecastClient = forecastClient;
    this.resilience = resilience;
    this.apiKey = apiKey;
    this.maxDias = maxDias;
  }

  @Override
  public Clima obtenerClima(Ubicacion ubicacion)
  {
    log.info("SIN CACHE - pegando a WeatherAPI current.json (ubicacion={})", ubicacion);

    WeatherApiCurrentResponse response = resilience.execute(() -> restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/current.json")
            .queryParam("key", apiKey)
            .queryParam("q", consultaUbicacion(ubicacion))
            .queryParam("aqi", "no")
            .build())
        .retrieve()
        .body(WeatherApiCurrentResponse.class));

    return response.aClima();
  }

  @Override
  public Clima obtenerPronostico(Ubicacion ubicacion, LocalDateTime fechaHorario)
  {
    int dias = diasNecesarios(fechaHorario);
    log.info("Pidiendo pronostico (ubicacion={}, fechaHorario={}, dias={}) - si no aparece "
        + "CACHE MISS a continuacion, esta sirviendo desde cache", ubicacion, fechaHorario, dias);

    WeatherApiForecastResponse response = forecastClient.forecastCrudo(ubicacion, dias);
    return response.climaEnHora(fechaHorario);
  }

  static String consultaUbicacion(Ubicacion ubicacion)
  {
    if (ubicacion.getLatitud() != null && ubicacion.getLongitud() != null)
      return ubicacion.getLatitud() + "," + ubicacion.getLongitud();

    return ubicacion.getBarrio();
  }

  /**
   * Dias de forecast a pedir para cubrir fechaHorario: WeatherAPI cuenta
   * "days" desde hoy (day 1 = hoy), y el plan tiene un tope
   * ({@code weatherapi.forecast.max-days}) por encima del cual se trunca en
   * silencio — nunca se pide mas que eso.
   */
  private int diasNecesarios(LocalDateTime fechaHorario)
  {
    long diasDesdeHoy = ChronoUnit.DAYS.between(LocalDate.now(), fechaHorario.toLocalDate()) + 1;
    return (int) Math.min(Math.max(diasDesdeHoy, 1), maxDias);
  }
}
