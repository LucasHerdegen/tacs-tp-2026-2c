package com.tacs.backend.services.implem.weatherapi;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import java.time.Duration;

/**
 * Fabrica un {@link WeatherApiResilience} con umbrales tan altos que nunca
 * se disparan, para usar en tests que verifican otra cosa (mapeo de JSON,
 * caching) y no quieren que la resiliencia interfiera. El comportamiento de
 * resiliencia en si se prueba en {@link WeatherApiResilienceTest}.
 */
final class ResilienceDePrueba
{
  private ResilienceDePrueba() {}

  static WeatherApiResilience permisiva()
  {
    CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
        .slidingWindowSize(1000)
        .minimumNumberOfCalls(1000)
        .build());
    RateLimiterRegistry rlRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
        .limitForPeriod(1000)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .timeoutDuration(Duration.ZERO)
        .build());
    RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());

    return new WeatherApiResilience(cbRegistry, rlRegistry, retryRegistry);
  }
}
