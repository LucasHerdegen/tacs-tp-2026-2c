package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.services.ProveedorClima;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// por ahora mockeo al proveedor del clima, luego se cambia
@Service
public class MockProveedorClima implements ProveedorClima {
  @Override
  public Clima obtenerClima(Ubicacion ubicacion) {
    return new Clima(10.5, 22.0, 15.0);
  }
  @Override
  public Clima obtenerPronostico(Ubicacion ubicacion, LocalDateTime fechaHorario) {
    return new Clima(5.0, 24.5, 12.0);
  }
}
