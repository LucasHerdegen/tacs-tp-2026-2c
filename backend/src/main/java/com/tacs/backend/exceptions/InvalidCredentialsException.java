package com.tacs.backend.exceptions;

public class InvalidCredentialsException extends RuntimeException
{
  public InvalidCredentialsException(String message)
  {
    super(message);
  }
}
