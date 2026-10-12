package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.AlternativaNotFoundException;
import com.tacs.backend.exceptions.CapacidadMaximaException;
import com.tacs.backend.exceptions.EstadoInvalidoException;
import com.tacs.backend.exceptions.InvalidCredentialsException;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.exceptions.NotificacionNotFoundException;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.QuorumInvalidoException;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.UsernameAlreadyExistsException;
import com.tacs.backend.exceptions.VotacionCerradaException;
import com.tacs.backend.exceptions.VotacionNotFoundException;
import com.tacs.backend.exceptions.YaEsParticipanteException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.HashMap;

@Slf4j
@ControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler
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

  @ExceptionHandler(YaEsParticipanteException.class)
  public ProblemDetail handleYaEsParticipanteException(YaEsParticipanteException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(NoParticipanteException.class)
  public ProblemDetail handleNoParticipanteException(NoParticipanteException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
  }

  @ExceptionHandler(NotificacionNotFoundException.class)
  public ProblemDetail handleNotificacionNotFoundException(NotificacionNotFoundException ex)
  {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
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
  public ProblemDetail handleOptimisticLockingFailureException(OptimisticLockingFailureException ex)
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
