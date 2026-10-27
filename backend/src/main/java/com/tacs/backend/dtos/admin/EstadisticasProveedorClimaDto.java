package com.tacs.backend.dtos.admin;

import com.tacs.backend.domain.clima.TipoProveedorClima;

import java.util.List;

public record EstadisticasProveedorClimaDto(
    TipoProveedorClima proveedor,
    String nombre,
    EstadoProveedorClima estadoActual,
    String estadoCircuitBreaker,
    long llamadasTotales,
    long llamadasExitosas,
    long erroresTotales,
    long respuestasInvalidas,
    long timeouts,
    long rechazosRateLimit,
    long fallbacksActivados,
    long cacheHits,
    long cacheMisses,
    double cacheHitRatio,
    List<PuntoEstadoProveedorDto> historialEstado)
{
}
