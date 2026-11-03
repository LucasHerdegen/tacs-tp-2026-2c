package com.tacs.backend.exceptions;

public class RangoReprogramacionInvalidoException extends RuntimeException
{
  private final String campo;

  public RangoReprogramacionInvalidoException(String message)
  {
    this(message, null);
  }

  public RangoReprogramacionInvalidoException(String message, String campo)
  {
    super(message);
    this.campo = campo;
  }

  public String getCampo()
  {
    return campo;
  }
}

