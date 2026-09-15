package com.tacs.backend.exceptions;

public class ProveedorClimaIndisponibleException extends RuntimeException
{
  public ProveedorClimaIndisponibleException(String message)
  {
    super(message);
  }

  public ProveedorClimaIndisponibleException(String message, Throwable cause)
  {
    super(message, cause);
  }
}
