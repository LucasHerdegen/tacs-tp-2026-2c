package com.tacs.backend.services.implem.weatherapi;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifica que WeatherApiProveedorClima arma bien el request HTTP (URL,
 * query params) y mapea correctamente la respuesta a Clima. No verifica
 * caching (eso lo hace WeatherApiProveedorClimaCacheTest, que necesita el
 * proxy de Spring para que @Cacheable tenga efecto).
 */
class WeatherApiProveedorClimaTest
{
  private static final Ubicacion PALERMO = new Ubicacion("Palermo", -34.58, -58.43);

  private WeatherApiProveedorClima construirConMockServer(MockRestServiceServer[] serverOut)
  {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weatherapi.com/v1");
    serverOut[0] = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();
    WeatherApiForecastClient forecastClient =
        new WeatherApiForecastClient(restClient, ResilienceDePrueba.permisiva(), "test-key");

    return new WeatherApiProveedorClima(restClient, forecastClient, ResilienceDePrueba.permisiva(), "test-key", 14);
  }

  @Test
  void obtenerClimaPideCurrentJsonConLaUbicacionYMapeaLaRespuesta()
  {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    WeatherApiProveedorClima proveedor = construirConMockServer(serverHolder);

    serverHolder[0].expect(requestTo("https://api.weatherapi.com/v1/current.json?key=test-key&q=-34.58,-58.43&aqi=no"))
        .andRespond(withSuccess("""
            { "current": { "temp_c": 22.0, "chance_of_rain": 15, "wind_kph": 12.0 } }
            """, MediaType.APPLICATION_JSON));

    Clima clima = proveedor.obtenerClima(PALERMO);

    assertThat(clima.getTemperatura()).isEqualTo(22.0);
    assertThat(clima.getProbabilidadLluvia()).isEqualTo(15);
    assertThat(clima.getViento()).isEqualTo(12.0);
    serverHolder[0].verify();
  }

  @Test
  void obtenerPronosticoPideForecastJsonConDiasAcotadosYMapeaLaHoraPedida()
  {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    WeatherApiProveedorClima proveedor = construirConMockServer(serverHolder);

    LocalDateTime fechaHorario = LocalDate.now().plusDays(1).atTime(15, 0);

    serverHolder[0].expect(requestTo(
            "https://api.weatherapi.com/v1/forecast.json?key=test-key&q=-34.58,-58.43&days=2&aqi=no&alerts=no"))
        .andRespond(withSuccess("""
            {
              "forecast": {
                "forecastday": [
                  { "date": "d0", "hour": [
                    { "time": "%s 15:00", "temp_c": 25.0, "chance_of_rain": 40, "wind_kph": 20.0 }
                  ] }
                ]
              }
            }
            """.formatted(fechaHorario.toLocalDate()), MediaType.APPLICATION_JSON));

    Clima clima = proveedor.obtenerPronostico(PALERMO, fechaHorario);

    assertThat(clima.getTemperatura()).isEqualTo(25.0);
    assertThat(clima.getProbabilidadLluvia()).isEqualTo(40);
    serverHolder[0].verify();
  }

  @Test
  void consultaPorBarrioCuandoLaUbicacionNoTieneCoordenadas()
  {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    WeatherApiProveedorClima proveedor = construirConMockServer(serverHolder);
    Ubicacion sinCoordenadas = new Ubicacion("Palermo", null, null);

    serverHolder[0].expect(requestTo("https://api.weatherapi.com/v1/current.json?key=test-key&q=Palermo&aqi=no"))
        .andRespond(withSuccess("""
            { "current": { "temp_c": 18.0, "chance_of_rain": 5, "wind_kph": 8.0 } }
            """, MediaType.APPLICATION_JSON));

    proveedor.obtenerClima(sinCoordenadas);

    serverHolder[0].verify();
  }
}
