package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.CapacidadMaximaException;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest
{
  @Test
  void proveedorClimaIndisponibleExceptionMapeaA503()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    ProveedorClimaIndisponibleException ex =
        new ProveedorClimaIndisponibleException("Proveedor de clima no disponible temporalmente");

    ProblemDetail problemDetail = handler.handleProveedorClimaIndisponibleException(ex);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    assertThat(problemDetail.getDetail()).isEqualTo("Proveedor de clima no disponible temporalmente");
  }

  @Test
  void capacidadMaximaExceptionMapeaA409Conflict()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    CapacidadMaximaException ex = new CapacidadMaximaException("Sin cupo disponible");

    ProblemDetail problemDetail = handler.handleCapacidadMaximaException(ex);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problemDetail.getDetail()).isEqualTo("Sin cupo disponible");
  }

  @Test
  void optimisticLockingFailureExceptionMapeaA409Conflict()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    OptimisticLockingFailureException ex =
        new OptimisticLockingFailureException("Conflicto de version");

    ProblemDetail problemDetail = handler.handleOptimisticLockingFailureException(ex);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problemDetail.getDetail()).contains("conflicto de concurrencia");
  }
}
