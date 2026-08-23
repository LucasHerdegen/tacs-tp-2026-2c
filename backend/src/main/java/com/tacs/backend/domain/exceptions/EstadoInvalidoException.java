package com.tacs.backend.domain.exceptions;

public class EstadoInvalidoException extends RuntimeException
{
  public EstadoInvalidoException(String message)
  {
    super(message);
  }
}
