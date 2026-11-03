package com.tacs.backend.services.implem.legacyclima;

import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.exceptions.RespuestaClimaInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyClimaValidadorTest
{
  private LegacyClimaValidador validador;

  @BeforeEach
  void setUp()
  {
    validador = new LegacyClimaValidador();
  }

  @Test
  void respuestaValidaSeMapeaCorrectamenteAClima()
  {
    String jsonValido = """
        {
          "id": "req-123",
          "status": "OK",
          "result": {
            "location": { "lat": -34.60, "lon": -58.38 },
            "timestamp": "2026-10-27T18:00:00Z",
            "condition": "partly cloudy",
            "temperature_c": 21.5,
            "feels_like_c": 21.0,
            "humidity_pct": 65,
            "precipitation_probability_pct": 30,
            "precipitation_mm": 1.2,
            "wind_kph": 14.8,
            "wind_direction": "SE"
          }
        }
        """;

    Clima clima = validador.validarYMapea(jsonValido, "req-123");

    assertThat(clima.getTemperatura()).isEqualTo(21.5);
    assertThat(clima.getProbabilidadLluvia()).isEqualTo(30.0);
    assertThat(clima.getViento()).isEqualTo(14.8);
  }

  @Test
  void cuerpoVacioONuloLanzaRespuestaClimaInvalidaException()
  {
    assertThatThrownBy(() -> validador.validarYMapea("", "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class)
        .hasMessageContaining("vacio");

    assertThatThrownBy(() -> validador.validarYMapea(null, "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class);
  }

  @Test
  void respuestaHtml502ConStatus200LanzaRespuestaClimaInvalidaException()
  {
    String html502 = "<html><head><title>502 Bad Gateway</title></head><body><h1>502 Bad Gateway</h1></body></html>";

    assertThatThrownBy(() -> validador.validarYMapea(html502, "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class)
        .hasMessageContaining("HTML");
  }

  @Test
  void jsonTruncadoOMalformadoLanzaRespuestaClimaInvalidaException()
  {
    String jsonTruncado = "{\"id\":\"req-1\",\"status\":\"OK\",\"result\":{\"temperature_c\":20.";
    String jsonComaFinal = "{\"id\":\"req-1\",\"status\":\"OK\",}";
    String jsonIgualEnVezDeDosPuntos = "{\"id\"=\"req-1\",\"status\":\"OK\"}";

    assertThatThrownBy(() -> validador.validarYMapea(jsonTruncado, "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class)
        .hasMessageContaining("JSON malformado");

    assertThatThrownBy(() -> validador.validarYMapea(jsonComaFinal, "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class);

    assertThatThrownBy(() -> validador.validarYMapea(jsonIgualEnVezDeDosPuntos, "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class);
  }

  @Test
  void idDeRespuestaDistintoAlEsperadoLanzaRespuestaClimaInvalidaException()
  {
    String jsonIdDistinto = """
        {
          "id": "req-otro",
          "status": "OK",
          "result": {
            "location": { "lat": -34.60, "lon": -58.38 },
            "temperature_c": 20.0,
            "precipitation_probability_pct": 10,
            "wind_kph": 10.0
          }
        }
        """;

    assertThatThrownBy(() -> validador.validarYMapea(jsonIdDistinto, "req-esperado"))
        .isInstanceOf(RespuestaClimaInvalidaException.class)
        .hasMessageContaining("no coincide");
  }

  @Test
  void respuestaConStatusErrorLanzaRespuestaClimaInvalidaException()
  {
    String jsonError = """
        {
          "id": "req-1",
          "status": "ERROR",
          "error": {
            "code": "INVALID_PARAMS",
            "message": "location.lat must be in [-90, 90]"
          }
        }
        """;

    assertThatThrownBy(() -> validador.validarYMapea(jsonError, "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class)
        .hasMessageContaining("INVALID_PARAMS");
  }

  @Test
  void valoresMeteorologicosFueraDeRangoLanzanRespuestaClimaInvalidaException()
  {
    String jsonTempAbsurda = """
        {
          "id": "req-1",
          "status": "OK",
          "result": {
            "location": { "lat": -34.60, "lon": -58.38 },
            "temperature_c": 130.0,
            "precipitation_probability_pct": 20,
            "wind_kph": 15.0
          }
        }
        """;

    String jsonLluviaInvalida = """
        {
          "id": "req-1",
          "status": "OK",
          "result": {
            "location": { "lat": -34.60, "lon": -58.38 },
            "temperature_c": 20.0,
            "precipitation_probability_pct": 140,
            "wind_kph": 15.0
          }
        }
        """;

    String jsonVientoNegativo = """
        {
          "id": "req-1",
          "status": "OK",
          "result": {
            "location": { "lat": -34.60, "lon": -58.38 },
            "temperature_c": 20.0,
            "precipitation_probability_pct": 20,
            "wind_kph": -5.0
          }
        }
        """;

    assertThatThrownBy(() -> validador.validarYMapea(jsonTempAbsurda, "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class)
        .hasMessageContaining("Temperatura");

    assertThatThrownBy(() -> validador.validarYMapea(jsonLluviaInvalida, "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class)
        .hasMessageContaining("Probabilidad");

    assertThatThrownBy(() -> validador.validarYMapea(jsonVientoNegativo, "req-1"))
        .isInstanceOf(RespuestaClimaInvalidaException.class)
        .hasMessageContaining("viento");
  }
}
