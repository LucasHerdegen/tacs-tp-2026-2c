package com.tacs.backend.repositories;

/**
 * Deltas de contadores operativos a aplicar de forma atomica sobre las
 * metricas persistidas de un proveedor de clima.
 */
public record IncrementosMetricasProveedor(
    long llamadasTotales,
    long llamadasExitosas,
    long erroresTotales,
    long respuestasInvalidas,
    long timeouts,
    long rechazosRateLimit,
    long fallbacksActivados,
    long cacheHits,
    long cacheMisses)
{
  public boolean estaVacio()
  {
    return llamadasTotales == 0
        && llamadasExitosas == 0
        && erroresTotales == 0
        && respuestasInvalidas == 0
        && timeouts == 0
        && rechazosRateLimit == 0
        && fallbacksActivados == 0
        && cacheHits == 0
        && cacheMisses == 0;
  }
}
