package com.tacs.backend.exceptions;

public class UsernameAlreadyExistsException extends RuntimeException
{
  public UsernameAlreadyExistsException(String message)
  {
    super(message);
  }
}
