package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.AlternativaNotFoundException;
import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.CapacidadMaximaException;
import com.tacs.backend.exceptions.EstadoInvalidoException;
import com.tacs.backend.exceptions.InvalidCredentialsException;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.VotacionCerradaException;
import com.tacs.backend.exceptions.VotacionNotFoundException;
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

  @ExceptionHandler(com.tacs.backend.exceptions.RangoReprogramacionInvalidoException.class)
  public ResponseEntity<String> handleRangoReprogramacionInvalidoException(com.tacs.backend.exceptions.RangoReprogramacionInvalidoException ex)
  {
    return ResponseEntity.badRequest().body(ex.getMessage());
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

  @ExceptionHandler(VotacionNotFoundException.class)
  public ResponseEntity<String> handleVotacionNotFoundException(VotacionNotFoundException ex)
  {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(AlternativaNotFoundException.class)
  public ResponseEntity<String> handleAlternativaNotFoundException(AlternativaNotFoundException ex)
  {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(VotacionCerradaException.class)
  public ResponseEntity<String> handleVotacionCerradaException(VotacionCerradaException ex)
  {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
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
