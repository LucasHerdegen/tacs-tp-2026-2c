package com.tacs.backend.services.implem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
public class OptimisticLockingRetry
{
  private static final Logger log = LoggerFactory.getLogger(OptimisticLockingRetry.class);

  private static final int DEFAULT_MAX_INTENTOS = 50;
  private static final long MIN_BACKOFF_MS = 10;
  private static final long MAX_BACKOFF_MS = 60;

  private final int maxIntentos;

  public OptimisticLockingRetry()
  {
    this(DEFAULT_MAX_INTENTOS);
  }

  public OptimisticLockingRetry(int maxIntentos)
  {
    this.maxIntentos = maxIntentos;
  }

  public <T> T ejecutarConReintento(Supplier<T> operacion)
  {
    return ejecutarConReintento(operacion, this.maxIntentos);
  }

  public void ejecutarConReintento(Runnable operacion)
  {
    ejecutarConReintento(() -> {
      operacion.run();
      return null;
    }, this.maxIntentos);
  }

  public <T> T ejecutarConReintento(Supplier<T> operacion, int maxReintentos)
  {
    int intento = 0;
    while (true)
    {
      try
      {
        return operacion.get();
      }
      catch (OptimisticLockingFailureException ex)
      {
        intento++;
        if (intento >= maxReintentos)
        {
          log.error("Se alcanzo el numero maximo de intentos ({}) por conflicto de bloqueo optimista", maxReintentos, ex);
          throw ex;
        }
        log.debug("Conflicto de bloqueo optimista detectado. Reintentando intento {}/{}", intento, maxReintentos);
        esperarConJitter();
      }
    }
  }

  private void esperarConJitter()
  {
    try
    {
      long espera = ThreadLocalRandom.current().nextLong(MIN_BACKOFF_MS, MAX_BACKOFF_MS + 1);
      Thread.sleep(espera);
    }
    catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Hilo interrumpido durante la espera de reintento de bloqueo optimista", e);
    }
  }
}
