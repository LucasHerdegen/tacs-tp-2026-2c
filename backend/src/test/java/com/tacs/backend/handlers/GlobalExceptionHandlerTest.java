package com.tacs.backend.handlers;

import com.tacs.backend.exceptions.CapacidadMaximaException;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.mockito.Mockito;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

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

  @Test
  void httpMessageNotReadableExceptionDevuelveMensajeAmigableEn400()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException("JSON parse error: Cannot deserialize", new MockHttpInputMessage(new byte[0]));
    WebRequest request = Mockito.mock(WebRequest.class);

    ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
    ProblemDetail problemDetail = (ProblemDetail) response.getBody();
    assertThat(problemDetail.getDetail()).isEqualTo("El formato de la solicitud no es válido o los datos enviados no son correctos");
  }

  @Test
  void methodArgumentTypeMismatchExceptionDevuelveMensajeAmigableEn400()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException("invalido", String.class, "tipo", null, null);

    ProblemDetail problemDetail = handler.handleMethodArgumentTypeMismatchException(ex);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getDetail()).contains("El parámetro 'tipo' contiene un valor con formato inválido");
  }

  @Test
  void patternSyntaxExceptionDevuelveMensajeAmigableEn400()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    PatternSyntaxException ex = new PatternSyntaxException("Dangling meta character '+'", "++++", 0);

    ProblemDetail problemDetail = handler.handlePatternSyntaxException(ex);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getDetail()).isEqualTo("El término de búsqueda contiene caracteres especiales no válidos");
  }

  @Test
  void exceptionInesperadaMapeaA500ConMensajeAmigableSinExponerStackTrace()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    Exception ex = new RuntimeException("NullPointerException oculta");

    ProblemDetail problemDetail = handler.handleAllOtherExceptions(ex);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problemDetail.getDetail()).isEqualTo("Ocurrió un error inesperado en el servidor. Por favor, intente nuevamente más tarde.");
  }

  @Test
  void rangoReprogramacionInvalidoExceptionDevuelveProblemDetailConErroresYMensajeLimpio()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    RangoReprogramacionInvalidoException ex =
        new RangoReprogramacionInvalidoException("El rango de reprogramacion no puede superar 3 dias: limitacion del plan de WeatherAPI contratado");

    ProblemDetail problemDetail = handler.handleRangoReprogramacionInvalidoException(ex);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getDetail()).isEqualTo("El rango de reprogramacion no puede superar 3 dias");
    assertThat(problemDetail.getProperties()).containsKey("errores");
  }

  @Test
  void illegalArgumentExceptionHorasAnticipacionDevuelveProblemDetailConErroresYMensajeLimpio()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    IllegalArgumentException ex =
        new IllegalArgumentException("horasAnticipacion no puede superar 72 horas (3 dias): limitacion del plan de WeatherAPI contratado");

    ProblemDetail problemDetail = handler.handleIllegalArgumentException(ex);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getDetail()).isEqualTo("horasAnticipacion no puede superar 72 horas (3 dias)");
    assertThat(problemDetail.getProperties()).containsKey("errores");
  }

  @Test
  void rangoReprogramacionInvalidoExceptionHorariaMapeaAHoraFinalEnErrores()
  {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    RangoReprogramacionInvalidoException ex =
        new RangoReprogramacionInvalidoException("La franja horaria permitida (2 hs) no puede ser menor a la duracion estimada de la actividad (4 hs)");

    ProblemDetail problemDetail = handler.handleRangoReprogramacionInvalidoException(ex);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getDetail()).contains("franja horaria permitida");
    assertThat(problemDetail.getProperties()).containsKey("errores");
    @SuppressWarnings("unchecked")
    Map<String, String> errores = (Map<String, String>) problemDetail.getProperties().get("errores");
    assertThat(errores).containsKey("horaFinal");
    assertThat(errores.get("horaFinal")).contains("franja horaria permitida");
  }
}
