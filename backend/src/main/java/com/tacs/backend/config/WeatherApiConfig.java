package com.tacs.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Arma el {@link RestClient} usado por WeatherApiProveedorClima, con baseUrl
 * y timeouts de conexion/lectura configurables por properties. 
 */

@Configuration
public class WeatherApiConfig
{
  @Bean
  public RestClient weatherApiRestClient(
      RestClient.Builder builder,
      @Value("${weatherapi.base-url}") String baseUrl,
      @Value("${weatherapi.connect-timeout-ms}") long connectTimeoutMs,
      @Value("${weatherapi.read-timeout-ms}") long readTimeoutMs)
  {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

    return builder
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .build();
  }
}
