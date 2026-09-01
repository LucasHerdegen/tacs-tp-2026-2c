package com.tacs.backend.services;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;

import java.time.LocalDateTime;

public interface ProveedorClima
{
  Clima obtenerClima(Ubicacion ubicacion);
  Clima obtenerPronostico(Ubicacion ubicacion, LocalDateTime fechaHorario);
}
