package com.tacs.backend.services.implem.weatherapi;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.tacs.backend.config.CacheConfig;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.services.ProveedorClima;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * A diferencia de WeatherApiProveedorClimaTest (unit test puro, sin contexto
 * de Spring), este test verifica el comportamiento de @Cacheable en si —
 * necesita el proxy AOP real de Spring, por eso levanta un contexto minimo
 * con @EnableCaching en vez de instanciar la clase con `new`.
 */
class WeatherApiProveedorClimaCacheTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);

  @Configuration
  @EnableCaching
  static class TestConfig
  {
    @Bean
    public CacheManager cacheManager()
    {
      CaffeineCacheManager cacheManager = new CaffeineCacheManager(CacheConfig.CACHE_FORECAST_POR_UBICACION);
      cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(60, TimeUnit.SECONDS));
      return cacheManager;
    }

    @Bean
    public WeatherApiForecastClient weatherApiForecastClient(RestClient restClient)
    {
      return new WeatherApiForecastClient(restClient, ResilienceDePrueba.permisiva(), "test-key");
    }

    @Bean
    public WeatherApiProveedorClima weatherApiProveedorClima(RestClient restClient, WeatherApiForecastClient forecastClient)
    {
      return new WeatherApiProveedorClima(restClient, forecastClient, ResilienceDePrueba.permisiva(), "test-key", 14);
    }
  }

  @Test
  void dosHorasDelMismoDiaDentroDelTtlGeneranUnaSolaLlamadaHttp()
  {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weatherapi.com/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    LocalDate manana = LocalDate.now().plusDays(1);

    // Una sola expectativa (ExpectedCount.once() por default): si el proveedor
    // pegara 2 veces, MockRestServiceServer falla el server.verify() de abajo.
    server.expect(requestTo(
            "https://api.weatherapi.com/v1/forecast.json?key=test-key&q=-34.58,-58.43&days=2&aqi=no&alerts=no"))
        .andRespond(withSuccess("""
            {
              "forecast": {
                "forecastday": [
                  { "date": "d0", "hour": [
                    { "time": "%s 12:00", "temp_c": 18.0, "chance_of_rain": 10, "wind_kph": 10.0 },
                    { "time": "%s 14:00", "temp_c": 20.0, "chance_of_rain": 20, "wind_kph": 15.0 }
                  ] }
                ]
              }
            }
            """.formatted(manana, manana), MediaType.APPLICATION_JSON));

    try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext())
    {
      ctx.getBeanFactory().registerSingleton("restClient", restClient);
      ctx.register(TestConfig.class);
      ctx.refresh();

      // Por interfaz: @Cacheable usa proxy JDK dinamico (WeatherApiProveedorClima
      // implementa ProveedorClima), asi que el proxy no es instancia de la
      // clase concreta, solo de la interfaz.
      ProveedorClima proveedor = ctx.getBean(ProveedorClima.class);

      LocalDateTime hora12 = manana.atTime(12, 0);
      LocalDateTime hora14 = manana.atTime(14, 0);

      proveedor.obtenerPronostico(UBICACION, hora12);
      proveedor.obtenerPronostico(UBICACION, hora14);
    }

    server.verify();
  }

  /**
   * Regresion del hallazgo real en la prueba manual: sin {@code sync = true}
   * en el {@code @Cacheable} de {@link WeatherApiForecastClient#forecastCrudo},
   * 2 threads que piden la MISMA key al mismo tiempo, con ninguno todavia
   * cacheado, disparan 2 llamadas HTTP reales en paralelo — cada una gasta su
   * propio cupo de rate limiter en vez de compartir 1 sola respuesta. La
   * respuesta mockeada se demora artificialmente (Thread.sleep) para
   * garantizar que la ventana de "cache miss" de ambos threads se solape,
   * en vez de depender de que la maquina sea lo bastante lenta como para
   * que coincida por casualidad.
   */
  @Test
  void dosThreadsConcurrentesParaLaMismaKeyGeneranUnaSolaLlamadaHttpGraciasASync() throws Exception
  {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weatherapi.com/v1");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    LocalDate manana = LocalDate.now().plusDays(1);

    server.expect(requestTo(
            "https://api.weatherapi.com/v1/forecast.json?key=test-key&q=-34.58,-58.43&days=2&aqi=no&alerts=no"))
        .andRespond(request ->
        {
          try
          {
            Thread.sleep(300);
          } catch (InterruptedException e)
          {
            Thread.currentThread().interrupt();
          }
          return withSuccess("""
              {
                "forecast": {
                  "forecastday": [
                    { "date": "d0", "hour": [
                      { "time": "%s 12:00", "temp_c": 18.0, "chance_of_rain": 10, "wind_kph": 10.0 }
                    ] }
                  ]
                }
              }
              """.formatted(manana), MediaType.APPLICATION_JSON).createResponse(request);
        });

    try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext())
    {
      ctx.getBeanFactory().registerSingleton("restClient", restClient);
      ctx.register(TestConfig.class);
      ctx.refresh();

      ProveedorClima proveedor = ctx.getBean(ProveedorClima.class);
      LocalDateTime hora12 = manana.atTime(12, 0);

      CountDownLatch arranqueSimultaneo = new CountDownLatch(1);
      Callable<Clima> pedirPronostico = () ->
      {
        arranqueSimultaneo.await();
        return proveedor.obtenerPronostico(UBICACION, hora12);
      };

      ExecutorService executor = Executors.newFixedThreadPool(2);
      Future<Clima> resultado1 = executor.submit(pedirPronostico);
      Future<Clima> resultado2 = executor.submit(pedirPronostico);
      arranqueSimultaneo.countDown();

      Clima clima1 = resultado1.get(5, TimeUnit.SECONDS);
      Clima clima2 = resultado2.get(5, TimeUnit.SECONDS);
      executor.shutdown();

      // Clima no tiene equals() propio (por identidad, via Object) y
      // climaEnHora() construye un Clima nuevo en cada llamada aunque el
      // WeatherApiForecastResponse de origen este cacheado y compartido — por
      // eso se comparan campos, no identidad. La prueba real de que hubo 1
      // sola llamada HTTP es el server.verify() de mas abajo.
      assertThat(clima1.getTemperatura()).isEqualTo(clima2.getTemperatura());
      assertThat(clima1.getProbabilidadLluvia()).isEqualTo(clima2.getProbabilidadLluvia());
      assertThat(clima1.getViento()).isEqualTo(clima2.getViento());
    }

    // Si sync=true no estuviera, este verify fallaria: MockRestServiceServer
    // esperaba 1 sola llamada (ExpectedCount.once() por default) y el segundo
    // thread habria disparado una 2da.
    server.verify();
  }
}
