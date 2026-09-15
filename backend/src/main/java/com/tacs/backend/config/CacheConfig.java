package com.tacs.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig
{
  public static final String CACHE_FORECAST_POR_UBICACION = "forecastPorUbicacionYRango";

  @Bean
  public CacheManager cacheManager(@Value("${weatherapi.cache.ttl-seconds}") long ttlSeconds)
  {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_FORECAST_POR_UBICACION);
    cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(ttlSeconds, TimeUnit.SECONDS));
    return cacheManager;
  }
}
