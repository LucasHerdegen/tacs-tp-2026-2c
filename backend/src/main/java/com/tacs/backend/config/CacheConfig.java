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
  public static final String CACHE_CLIMA_ACTUAL_POR_UBICACION = "climaActualPorUbicacion";
  public static final String CACHE_LEGACY_FORECAST = "legacyForecastPorUbicacionYHora";

  @Bean
  public CacheManager cacheManager(
      @Value("${weatherapi.cache.ttl-seconds:1800}") long ttlSeconds,
      @Value("${weatherapi.cache.current-ttl-seconds:300}") long currentTtlSeconds)
  {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager();
    cacheManager.registerCustomCache(
        CACHE_FORECAST_POR_UBICACION,
        Caffeine.newBuilder()
            .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
            .recordStats()
            .build());
    cacheManager.registerCustomCache(
        CACHE_CLIMA_ACTUAL_POR_UBICACION,
        Caffeine.newBuilder()
            .expireAfterWrite(currentTtlSeconds, TimeUnit.SECONDS)
            .recordStats()
            .build());
    cacheManager.registerCustomCache(
        CACHE_LEGACY_FORECAST,
        Caffeine.newBuilder()
            .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
            .recordStats()
            .build());
    return cacheManager;
  }
}
