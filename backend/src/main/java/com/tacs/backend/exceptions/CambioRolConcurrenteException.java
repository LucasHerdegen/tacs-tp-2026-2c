package com.tacs.backend.exceptions;

public class CambioRolConcurrenteException extends RuntimeException
{
  public CambioRolConcurrenteException(String message)
  {
    super(message);
  }
}
