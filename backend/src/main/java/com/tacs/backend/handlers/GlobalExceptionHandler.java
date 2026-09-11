package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;

import java.util.Map;
import java.util.HashMap;

import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;

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

  @ExceptionHandler(RangoReprogramacionInvalidoException.class)
  public ProblemDetail handleRangoReprogramacionInvalidoException(
      RangoReprogramacionInvalidoException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(AccesoDenegadoException.class)
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

  @ExceptionHandler(QuorumInvalidoException.class)
  public ProblemDetail handleQuorumInvalidoException(QuorumInvalidoException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex)
  {
    if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("no encontrad"))
    {
      return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ProblemDetail handleIllegalStateException(IllegalStateException ex)
  {
    if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("no particip"))
    {
      return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex)
  {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
        "Error de validacion en los campos enviados");
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult().getAllErrors().forEach((error) -> {
      String fieldName = ((FieldError) error).getField();
      String errorMessage = error.getDefaultMessage();
      errors.put(fieldName, errorMessage);
    });
    problemDetail.setProperty("errores", errors);
    return problemDetail;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleAllOtherExceptions(Exception ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
        "Ocurrio un error inesperado. Por favor, intente nuevamente mas tarde.");
  }
}
