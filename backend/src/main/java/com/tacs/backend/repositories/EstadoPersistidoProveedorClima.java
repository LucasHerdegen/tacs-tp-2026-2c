package com.tacs.backend.repositories;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.dtos.admin.PuntoEstadoProveedorDto;

import java.util.List;

/**
 * Instantanea de las metricas acumuladas e historial de estado persistidos
 * para un proveedor de clima.
 */
public record EstadoPersistidoProveedorClima(
    TipoProveedorClima proveedor,
    EstadoProveedorClima estadoActual,
    EstadoProveedorClima estadoSondeoConfirmado,
    long llamadasTotales,
    long llamadasExitosas,
    long erroresTotales,
    long respuestasInvalidas,
    long timeouts,
    long rechazosRateLimit,
    long fallbacksActivados,
    long cacheHits,
    long cacheMisses,
    List<PuntoEstadoProveedorDto> historialEstado)
{
}
