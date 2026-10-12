package com.tacs.backend.services.implem;

import com.tacs.backend.exceptions.CapacidadMaximaException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisticLockingRetryTest
{
  @Test
  void ejecutaOperacionExitosaSinReintentos()
  {
    OptimisticLockingRetry retry = new OptimisticLockingRetry(5);
    AtomicInteger ejecuciones = new AtomicInteger(0);

    String resultado = retry.ejecutarConReintento(() -> {
      ejecuciones.incrementAndGet();
      return "exito";
    });

    assertThat(resultado).isEqualTo("exito");
    assertThat(ejecuciones.get()).isEqualTo(1);
  }

  @Test
  void reintentaAnteOptimisticLockingFailureExceptionYTerminaConExito()
  {
    OptimisticLockingRetry retry = new OptimisticLockingRetry(5);
    AtomicInteger intentos = new AtomicInteger(0);

    String resultado = retry.ejecutarConReintento(() -> {
      int count = intentos.incrementAndGet();
      if (count < 3)
      {
        throw new OptimisticLockingFailureException("Conflicto de version simulado");
      }
      return "resuelto";
    });

    assertThat(resultado).isEqualTo("resuelto");
    assertThat(intentos.get()).isEqualTo(3);
  }

  @Test
  void superaMaximoDeIntentosYLanzaOptimisticLockingFailureException()
  {
    OptimisticLockingRetry retry = new OptimisticLockingRetry(3);
    AtomicInteger intentos = new AtomicInteger(0);

    assertThatThrownBy(() -> retry.ejecutarConReintento(() -> {
      intentos.incrementAndGet();
      throw new OptimisticLockingFailureException("Siempre falla");
    })).isInstanceOf(OptimisticLockingFailureException.class);

    assertThat(intentos.get()).isEqualTo(3);
  }

  @Test
  void excepcionesDeNegocioNoSeReintentanSePropaganInmediatamente()
  {
    OptimisticLockingRetry retry = new OptimisticLockingRetry(5);
    AtomicInteger intentos = new AtomicInteger(0);

    assertThatThrownBy(() -> retry.ejecutarConReintento(() -> {
      intentos.incrementAndGet();
      throw new CapacidadMaximaException("Sin cupo");
    })).isInstanceOf(CapacidadMaximaException.class);

    assertThat(intentos.get()).isEqualTo(1);
  }
}
