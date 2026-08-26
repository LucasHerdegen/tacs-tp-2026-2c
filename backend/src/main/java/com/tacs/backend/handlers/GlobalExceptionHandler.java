package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.EstadoInvalidoException;
import com.tacs.backend.exceptions.InvalidCredentialsException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.UsernameAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
class GlobalExceptionHandler
{
  @ExceptionHandler(EstadoInvalidoException.class)
  public ResponseEntity<String> handleEstadoInvalidoException(EstadoInvalidoException ex)
  {
    return ResponseEntity.badRequest().body(ex.getMessage());
  }

  @ExceptionHandler(UsuarioNotFoundException.class)
  public ResponseEntity<String> handleUsuarioNotFoundException(UsuarioNotFoundException ex)
  {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(com.tacs.backend.exceptions.ActividadNotFoundException.class)
  public ResponseEntity<String> handleActividadNotFoundException(com.tacs.backend.exceptions.ActividadNotFoundException ex)
  {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(com.tacs.backend.exceptions.AccesoDenegadoException.class)
  public ResponseEntity<String> handleAccesoDenegadoException(com.tacs.backend.exceptions.AccesoDenegadoException ex)
  {
    return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(ex.getMessage());
  }

  @ExceptionHandler(UsernameAlreadyExistsException.class)
  public ResponseEntity<String> handleUsernameAlreadyExistsException(UsernameAlreadyExistsException ex)
  {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<String> handleInvalidCredentialsException(InvalidCredentialsException ex)
  {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
  }
}
