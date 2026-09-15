package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.services.ProveedorClima;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Bean test-only: solo se registra bajo el profile "mock-clima"
 * No tiene resiliencia propia (cache/retry) — es test-only, no necesita ninguna.
 * Se invoca utilizando profiles directamente al levantar la APP
 */
@Profile("mock-clima")
@Service
public class MockProveedorClima implements ProveedorClima
{
  @Override
  public Clima obtenerClima(Ubicacion ubicacion)
  {
    return new Clima(10.5, 22.0, 15.0);
  }

  @Override
  public Clima obtenerPronostico(Ubicacion ubicacion, LocalDateTime fechaHorario)
  {
    return new Clima(5.0, 24.5, 12.0);
  }
}
