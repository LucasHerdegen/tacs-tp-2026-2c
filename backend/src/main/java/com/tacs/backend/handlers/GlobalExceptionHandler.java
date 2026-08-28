package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.CapacidadMaximaException;
import com.tacs.backend.exceptions.EstadoInvalidoException;
import com.tacs.backend.exceptions.InvalidCredentialsException;
import com.tacs.backend.exceptions.NoParticipanteException;
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
  @ExceptionHandler(ActividadNotFoundException.class)
  public ResponseEntity<String> handleActividadNotFoundException(ActividadNotFoundException ex) {
    return ResponseEntity.notFound().build();
  }
  @ExceptionHandler(CapacidadMaximaException.class)
  public ResponseEntity<String> handleCapacidadMaximaException(CapacidadMaximaException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
  }
  @ExceptionHandler(NoParticipanteException.class)
  public ResponseEntity<String> handleNoParticipanteException(NoParticipanteException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
  }
}
