package com.tacs.backend.exceptions;

public class VotacionNotFoundException extends RuntimeException
{
  public VotacionNotFoundException(String message)
  {
    super(message);
  }
}