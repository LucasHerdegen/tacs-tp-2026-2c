package com.tacs.backend.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone la instancia global de OpenTelemetry como bean de Spring para instrumentación manual.
 */
@Configuration
public class ObservabilidadConfig
{
  /**
   * Devuelve el proveedor global configurado por el agente de OpenTelemetry o no-op en tests.
   *
   * @return instancia de {@link OpenTelemetry}.
   */
  @Bean
  public OpenTelemetry openTelemetry()
  {
    return GlobalOpenTelemetry.get();
  }
}
