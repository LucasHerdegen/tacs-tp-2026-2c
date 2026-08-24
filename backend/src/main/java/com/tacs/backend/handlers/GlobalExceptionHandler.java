package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.EstadoInvalidoException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
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
}
