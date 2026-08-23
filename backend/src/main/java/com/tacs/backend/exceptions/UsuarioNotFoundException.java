package com.tacs.backend.exceptions;

public class UsuarioNotFoundException extends RuntimeException
{
  public UsuarioNotFoundException(String message)
  {
    super(message);
  }
}
