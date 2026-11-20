package com.tacs.backend.exceptions;

/**
 * Excepcion lanzada cuando se excede el limite de tasa (rate limit) hacia un
 * proveedor de clima. Extiende {@link ProveedorClimaIndisponibleException} para
 * que el selector pueda activar fallback automatico, pero permite diferenciarla
 * en {@code GlobalExceptionHandler} como HTTP 429 en lugar de 503.
 */
public class RateLimitClimaExcedidoException extends ProveedorClimaIndisponibleException
{
  public RateLimitClimaExcedidoException(String message, Throwable cause)
  {
    super(message, cause);
  }
}
