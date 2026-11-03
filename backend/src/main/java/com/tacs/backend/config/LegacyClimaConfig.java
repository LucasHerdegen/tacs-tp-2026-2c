package com.tacs.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configura el {@link RestClient} dedicado al proveedor de clima legacy
 * (Humedad, Datos y Pronosticos S.A.), aplicando timeouts agresivos de
 * conexion y lectura para cortar colgados indefinidos del binario.
 */
@Configuration
public class LegacyClimaConfig
{
  @Bean("legacyClimaRestClient")
  public RestClient legacyClimaRestClient(
      RestClient.Builder builder,
      @Value("${legacy.clima.base-url:http://localhost:9977}") String baseUrl,
      @Value("${legacy.clima.connect-timeout-ms:1000}") long connectTimeoutMs,
      @Value("${legacy.clima.read-timeout-ms:1500}") long readTimeoutMs)
  {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

    return builder.clone()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .build();
  }
}
