package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import org.junit.jupiter.api.Test;
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
}
