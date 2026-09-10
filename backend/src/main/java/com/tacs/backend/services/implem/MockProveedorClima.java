package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.services.ProveedorClima;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// por ahora mockeo al proveedor del clima, luego se cambia
@Service
public class MockProveedorClima implements ProveedorClima
{
  @Override
  @Cacheable(value = "climaActual", key = "#ubicacion")
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
  public Clima obtenerClima(Ubicacion ubicacion)
  {
    return new Clima(10.5, 22.0, 15.0);
  }

  @Override
  @Cacheable(value = "pronostico", key = "{#ubicacion, #fechaHorario}")
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
  public Clima obtenerPronostico(Ubicacion ubicacion, LocalDateTime fechaHorario)
  {
    return new Clima(5.0, 24.5, 12.0);
  }

  @Recover
  public Clima fallbackClima(Exception e, Ubicacion ubicacion)
  {
    return new Clima(0, 0, 0); // fallback basico
  }

  @Recover
  public Clima fallbackPronostico(Exception e, Ubicacion ubicacion, LocalDateTime fechaHorario)
  {
    return new Clima(0, 0, 0); // fallback basico
  }
}
