package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;

import java.util.Map;
import java.util.HashMap;

import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;
import org.springframework.web.context.request.WebRequest;

@Slf4j
@ControllerAdvice
class GlobalExceptionHandler extends org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
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

  @ExceptionHandler(ProveedorClimaIndisponibleException.class)
  public ProblemDetail handleProveedorClimaIndisponibleException(ProveedorClimaIndisponibleException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
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

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLockingFailureException(org.springframework.dao.OptimisticLockingFailureException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Hubo un conflicto de concurrencia al actualizar el recurso, intente nuevamente.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request)
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
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleAllOtherExceptions(Exception ex)
  {
    log.error("Ocurrió un error inesperado", ex);
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
        "Ocurrio un error inesperado. Por favor, intente nuevamente mas tarde.");
  }
}
