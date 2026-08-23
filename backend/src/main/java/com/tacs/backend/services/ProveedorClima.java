package com.tacs.backend.services;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;

public interface ProveedorClima
{
  Clima obtenerClima(Ubicacion ubicacion);
}
