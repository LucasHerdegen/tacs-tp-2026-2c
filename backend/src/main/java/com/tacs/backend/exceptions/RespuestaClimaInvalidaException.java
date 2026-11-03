package com.tacs.backend.exceptions;

public class RespuestaClimaInvalidaException extends RuntimeException
{
  public RespuestaClimaInvalidaException(String message)
  {
    super(message);
  }

  public RespuestaClimaInvalidaException(String message, Throwable cause)
  {
    super(message, cause);
  }
}
