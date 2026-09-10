package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
class GlobalExceptionHandler
{
  @ExceptionHandler(EstadoInvalidoException.class)
  public ProblemDetail handleEstadoInvalidoException(EstadoInvalidoException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(UsuarioNotFoundException.class)
  public ProblemDetail handleUsuarioNotFoundException(UsuarioNotFoundException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(com.tacs.backend.exceptions.RangoReprogramacionInvalidoException.class)
  public ProblemDetail handleRangoReprogramacionInvalidoException(
      RangoReprogramacionInvalidoException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(com.tacs.backend.exceptions.AccesoDenegadoException.class)
  public ProblemDetail handleAccesoDenegadoException(AccesoDenegadoException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
  }

  @ExceptionHandler(UsernameAlreadyExistsException.class)
  public ProblemDetail handleUsernameAlreadyExistsException(UsernameAlreadyExistsException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ProblemDetail handleInvalidCredentialsException(InvalidCredentialsException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(VotacionNotFoundException.class)
  public ProblemDetail handleVotacionNotFoundException(VotacionNotFoundException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(AlternativaNotFoundException.class)
  public ProblemDetail handleAlternativaNotFoundException(AlternativaNotFoundException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(VotacionCerradaException.class)
  public ProblemDetail handleVotacionCerradaException(VotacionCerradaException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(ActividadNotFoundException.class)
  public ProblemDetail handleActividadNotFoundException(ActividadNotFoundException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(CapacidadMaximaException.class)
  public ProblemDetail handleCapacidadMaximaException(CapacidadMaximaException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(NoParticipanteException.class)
  public ProblemDetail handleNoParticipanteException(NoParticipanteException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
  }
}
