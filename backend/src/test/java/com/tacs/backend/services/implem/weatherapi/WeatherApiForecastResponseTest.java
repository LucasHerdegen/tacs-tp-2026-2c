package com.tacs.backend.services.implem.weatherapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeatherApiForecastResponseTest
{
  // JSON de ejemplo con el shape real confirmado contra la cuenta Business
  // (ver research.md, Unknown 5): forecast.forecastday[].hour[] con time,
  // temp_c, chance_of_rain, wind_kph, mas muchos otros campos que se ignoran.
  private static final String JSON_EJEMPLO = """
      {
        "location": { "name": "Palermo" },
        "current": { "temp_c": 8.8, "chance_of_rain": 20, "wind_kph": 18.7, "humidity": 76 },
        "forecast": {
          "forecastday": [
            {
              "date": "2026-09-20",
              "hour": [
                {
                  "time": "2026-09-20 12:00",
                  "temp_c": 18.0,
                  "chance_of_rain": 5,
                  "wind_kph": 10.0,
                  "humidity": 60
                },
                {
                  "time": "2026-09-20 14:00",
                  "temp_c": 20.0,
                  "chance_of_rain": 10,
                  "wind_kph": 15.0,
                  "humidity": 55
                }
              ]
            },
            {
              "date": "2026-09-21",
              "hour": [
                {
                  "time": "2026-09-21 14:00",
                  "temp_c": 22.0,
                  "chance_of_rain": 80,
                  "wind_kph": 30.0,
                  "humidity": 90
                }
              ]
            }
          ]
        }
      }
      """;

  private WeatherApiForecastResponse response;

  @BeforeEach
  void parsearRespuestaDeEjemplo() throws Exception
  {
    response = new ObjectMapper().readValue(JSON_EJEMPLO, WeatherApiForecastResponse.class);
  }

  @Test
  void mapeaLaHoraExacta()
  {
    Clima clima = response.climaEnHora(LocalDateTime.of(2026, 9, 20, 14, 0));

    assertThat(clima.getProbabilidadLluvia()).isEqualTo(10);
    assertThat(clima.getTemperatura()).isEqualTo(20.0);
    assertThat(clima.getViento()).isEqualTo(15.0);
  }

  @Test
  void mapeaLaHoraMasCercanaCuandoNoHayCoincidenciaExacta()
  {
    // 14:20 esta mas cerca de 14:00 que de 12:00
    Clima clima = response.climaEnHora(LocalDateTime.of(2026, 9, 20, 14, 20));

    assertThat(clima.getTemperatura()).isEqualTo(20.0);
  }

  @Test
  void distingueHorasDelMismoNumeroEnDiasDistintos()
  {
    Clima clima = response.climaEnHora(LocalDateTime.of(2026, 9, 21, 14, 0));

    assertThat(clima.getProbabilidadLluvia()).isEqualTo(80);
  }

  @Test
  void lanzaIndisponibleCuandoLaFechaPedidaEstaFueraDeLaCoberturaTraida()
  {
    // 10 dias despues del ultimo dato traido (2026-09-21) esta muy lejos de cualquier hora del payload
    assertThatThrownBy(() -> response.climaEnHora(LocalDateTime.of(2026, 10, 1, 14, 0)))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);
  }
}
