package com.tacs.backend.services.implem.legacyclima;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.RespuestaClimaInvalidaException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LegacyProveedorClimaTest
{
  private static final String TOKEN_PRUEBA = "token-firmado-utn";
  private static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private TimeZone timeZoneOriginal;

  @BeforeEach
  void setUp()
  {
    timeZoneOriginal = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone(ZONA_ARGENTINA));
  }

  @AfterEach
  void tearDown()
  {
    TimeZone.setDefault(timeZoneOriginal);
  }

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
  @DisplayName("obtenerPronostico normaliza coordenadas y convierte hora local argentina (16:42) a 19:00:00Z")
  void obtenerPronostico_horaLocalArgentina_normalizaCoordenadasYConvierteAUtcEnPostRpc()
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
          assertThat(body.at("/params/timestamp").asText()).isEqualTo("2026-10-28T19:00:00Z");

          String respuesta = """
              {
                "id": "%s",
                "status": "OK",
                "result": {
                  "location": { "lat": -34.58, "lon": -58.43 },
                  "timestamp": "2026-10-28T19:00:00Z",
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
  @DisplayName("obtenerPronostico convierte actividad a la 01:42 hora argentina a 04:00:00Z en RPC")
  void obtenerPronostico_actividadALaUnaYCuarentaYDosHoraArgentina_enviaCuatroUtcTruncado()
  {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    LegacyProveedorClima proveedor = construirConMockServer(serverHolder);

    Ubicacion ubicacion = new Ubicacion("CABA", -34.6037, -58.3816);
    LocalDateTime fechaHorario = LocalDateTime.of(2026, 10, 28, 1, 42, 0);

    serverHolder[0].expect(requestTo("http://clima-legacy:9978/rpc"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(request ->
        {
          MockClientHttpRequest mockReq = (MockClientHttpRequest) request;
          JsonNode body = MAPPER.readTree(mockReq.getBodyAsBytes());
          String reqId = body.get("id").asText();
          assertThat(body.at("/params/timestamp").asText()).isEqualTo("2026-10-28T04:00:00Z");

          String respuesta = """
              {
                "id": "%s",
                "status": "OK",
                "result": {
                  "location": { "lat": -34.60, "lon": -58.38 },
                  "timestamp": "2026-10-28T04:00:00Z",
                  "condition": "rain",
                  "temperature_c": 17.4,
                  "feels_like_c": 17.0,
                  "humidity_pct": 88,
                  "precipitation_probability_pct": 86,
                  "precipitation_mm": 6.4,
                  "wind_kph": 19.2,
                  "wind_direction": "S"
                }
              }
              """.formatted(reqId);
          return withSuccess(respuesta, MediaType.APPLICATION_JSON).createResponse(request);
        });

    Clima clima = proveedor.obtenerPronostico(ubicacion, fechaHorario);

    assertThat(clima.getProbabilidadLluvia()).isEqualTo(86.0);
    assertThat(clima.getTemperatura()).isEqualTo(17.4);
    assertThat(clima.getViento()).isEqualTo(19.2);
    serverHolder[0].verify();
  }

  @Test
  @DisplayName("obtenerPronostico convierte 22:45 hora argentina al dia siguiente 01:00:00Z")
  void obtenerPronostico_horaNocturnaArgentinaCruzaMedianocheUtc_enviaDiaSiguienteUtc()
  {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    LegacyProveedorClima proveedor = construirConMockServer(serverHolder);

    Ubicacion ubicacion = new Ubicacion("Palermo", -34.58, -58.43);
    LocalDateTime fechaHorario = LocalDateTime.of(2026, 10, 28, 22, 45, 30);

    serverHolder[0].expect(requestTo("http://clima-legacy:9978/rpc"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(request ->
        {
          MockClientHttpRequest mockReq = (MockClientHttpRequest) request;
          JsonNode body = MAPPER.readTree(mockReq.getBodyAsBytes());
          String reqId = body.get("id").asText();
          assertThat(body.at("/params/timestamp").asText()).isEqualTo("2026-10-29T01:00:00Z");

          String respuesta = """
              {
                "id": "%s",
                "status": "OK",
                "result": {
                  "location": { "lat": -34.58, "lon": -58.43 },
                  "timestamp": "2026-10-29T01:00:00Z",
                  "temperature_c": 18.0,
                  "precipitation_probability_pct": 20,
                  "wind_kph": 9.5
                }
              }
              """.formatted(reqId);
          return withSuccess(respuesta, MediaType.APPLICATION_JSON).createResponse(request);
        });

    Clima clima = proveedor.obtenerPronostico(ubicacion, fechaHorario);

    assertThat(clima.getTemperatura()).isEqualTo(18.0);
    serverHolder[0].verify();
  }

  @Test
  @DisplayName("obtenerPronostico trunca en UTC luego de convertir la zona local, consistente con el binario")
  void obtenerPronostico_zonaConDesplazamientoDeMediaHora_truncaEnUtcComoElBinario()
  {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    LegacyProveedorClima proveedor = construirConMockServer(serverHolder);

    Ubicacion ubicacion = new Ubicacion("CABA", -34.60, -58.38);
    LocalDateTime fechaHorario = LocalDateTime.of(2026, 10, 28, 10, 45, 0);

    serverHolder[0].expect(requestTo("http://clima-legacy:9978/rpc"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(request ->
        {
          MockClientHttpRequest mockReq = (MockClientHttpRequest) request;
          JsonNode body = MAPPER.readTree(mockReq.getBodyAsBytes());
          String reqId = body.get("id").asText();
          assertThat(body.at("/params/timestamp").asText()).isEqualTo("2026-10-28T05:00:00Z");

          String respuesta = """
              {
                "id": "%s",
                "status": "OK",
                "result": {
                  "location": { "lat": -34.60, "lon": -58.38 },
                  "timestamp": "2026-10-28T05:00:00Z",
                  "temperature_c": 22.0,
                  "precipitation_probability_pct": 15,
                  "wind_kph": 10.0
                }
              }
              """.formatted(reqId);
          return withSuccess(respuesta, MediaType.APPLICATION_JSON).createResponse(request);
        });

    Clima clima = proveedor.obtenerPronostico(ubicacion, fechaHorario);

    assertThat(clima.getTemperatura()).isEqualTo(22.0);
    serverHolder[0].verify();
  }

  @Test
  @DisplayName("obtenerPronostico rechaza respuesta RPC cuyo timestamp no coincide con el enviado")
  void obtenerPronostico_respuestaConTimestampDesfasado_lanzaExcepcion()
  {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    LegacyProveedorClima proveedor = construirConMockServer(serverHolder);

    Ubicacion ubicacion = new Ubicacion("CABA", -34.60, -58.38);
    LocalDateTime fechaHorario = LocalDateTime.of(2026, 10, 28, 1, 42, 0);

    serverHolder[0].expect(requestTo("http://clima-legacy:9978/rpc"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(request ->
        {
          MockClientHttpRequest mockReq = (MockClientHttpRequest) request;
          JsonNode body = MAPPER.readTree(mockReq.getBodyAsBytes());
          String reqId = body.get("id").asText();
          assertThat(body.at("/params/timestamp").asText()).isEqualTo("2026-10-28T04:00:00Z");

          String respuesta = """
              {
                "id": "%s",
                "status": "OK",
                "result": {
                  "location": { "lat": -34.60, "lon": -58.38 },
                  "timestamp": "2026-10-28T01:00:00Z",
                  "temperature_c": 20.0,
                  "precipitation_probability_pct": 11,
                  "wind_kph": 10.0
                }
              }
              """.formatted(reqId);
          return withSuccess(respuesta, MediaType.APPLICATION_JSON).createResponse(request);
        });

    assertThatThrownBy(() -> proveedor.obtenerPronostico(ubicacion, fechaHorario))
        .isInstanceOf(ProveedorClimaIndisponibleException.class)
        .hasCauseInstanceOf(RespuestaClimaInvalidaException.class);
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
