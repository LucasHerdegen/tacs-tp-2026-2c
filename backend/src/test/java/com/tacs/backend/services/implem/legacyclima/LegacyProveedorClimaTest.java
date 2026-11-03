package com.tacs.backend.services.implem.legacyclima;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LegacyProveedorClimaTest
{
  private static final String TOKEN_PRUEBA = "token-firmado-utn";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LegacyProveedorClima construirConMockServer(MockRestServiceServer[] serverOut)
  {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://clima-legacy:9978");
    serverOut[0] = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    LegacyClimaResilience resilience = new LegacyClimaResilience(
        CircuitBreakerRegistry.ofDefaults(),
        RateLimiterRegistry.ofDefaults(),
        RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build()),
        100,
        Duration.ofSeconds(1));

    LegacyForecastClient forecastClient = new LegacyForecastClient(
        restClient,
        resilience,
        new LegacyClimaValidador(),
        TOKEN_PRUEBA);

    return new LegacyProveedorClima(restClient, forecastClient);
  }

  @Test
  void obtenerPronosticoNormalizaCoordenadasYHoraYEnviaBearerTokenEnPostRpc()
  {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    LegacyProveedorClima proveedor = construirConMockServer(serverHolder);

    Ubicacion ubicacion = new Ubicacion("Palermo", -34.58432, -58.43198);
    LocalDateTime fechaHorario = LocalDateTime.of(2026, 10, 28, 16, 42, 19);

    serverHolder[0].expect(requestTo("http://clima-legacy:9978/rpc"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_PRUEBA))
        .andRespond(request ->
        {
          MockClientHttpRequest mockReq = (MockClientHttpRequest) request;
          JsonNode body = MAPPER.readTree(mockReq.getBodyAsBytes());
          String reqId = body.get("id").asText();
          assertThat(body.get("method").asText()).isEqualTo("weather.forecast");
          assertThat(body.at("/params/location/lat").asDouble()).isEqualTo(-34.58);
          assertThat(body.at("/params/location/lon").asDouble()).isEqualTo(-58.43);
          assertThat(body.at("/params/timestamp").asText()).isEqualTo("2026-10-28T16:00:00Z");

          String respuesta = """
              {
                "id": "%s",
                "status": "OK",
                "result": {
                  "location": { "lat": -34.58, "lon": -58.43 },
                  "timestamp": "2026-10-28T16:00:00Z",
                  "condition": "clear",
                  "temperature_c": 24.3,
                  "feels_like_c": 24.0,
                  "humidity_pct": 52,
                  "precipitation_probability_pct": 10,
                  "precipitation_mm": 0.0,
                  "wind_kph": 11.5,
                  "wind_direction": "NE"
                }
              }
              """.formatted(reqId);
          return withSuccess(respuesta, MediaType.APPLICATION_JSON).createResponse(request);
        });

    Clima clima = proveedor.obtenerPronostico(ubicacion, fechaHorario);

    assertThat(clima.getTemperatura()).isEqualTo(24.3);
    assertThat(clima.getProbabilidadLluvia()).isEqualTo(10.0);
    assertThat(clima.getViento()).isEqualTo(11.5);
    serverHolder[0].verify();
  }

  @Test
  void verificarSaludDistingueEstadoOkDegradadoYCaidoSegunHealthzYReadyz()
  {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    LegacyProveedorClima proveedor = construirConMockServer(serverHolder);

    serverHolder[0].expect(requestTo("http://clima-legacy:9978/healthz"))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
    serverHolder[0].expect(requestTo("http://clima-legacy:9978/readyz"))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
    assertThat(proveedor.verificarSalud()).isEqualTo(EstadoProveedorClima.OK);
    serverHolder[0].verify();

    serverHolder[0].reset();
    serverHolder[0].expect(requestTo("http://clima-legacy:9978/healthz"))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
    serverHolder[0].expect(requestTo("http://clima-legacy:9978/readyz"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("not ready\n"));
    assertThat(proveedor.verificarSalud()).isEqualTo(EstadoProveedorClima.DEGRADADO);
    serverHolder[0].verify();

    serverHolder[0].reset();
    serverHolder[0].expect(requestTo("http://clima-legacy:9978/healthz"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("unresponsive\n"));
    assertThat(proveedor.verificarSalud()).isEqualTo(EstadoProveedorClima.CAIDO);
    serverHolder[0].verify();
  }
}
