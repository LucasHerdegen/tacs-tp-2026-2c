package com.tacs.backend.domain.actividad;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Estados
{
  private static final Map<TipoEstadoActividad, EstadoActividad> estados = new EnumMap<>(TipoEstadoActividad.class);

  static
  {
    estados.put(TipoEstadoActividad.PROPUESTA, new EstadoActividad(
        TipoEstadoActividad.PROPUESTA,
        List.of(TipoEstadoActividad.CONFIRMADA, TipoEstadoActividad.CANCELADA, TipoEstadoActividad.REPROGRAMADA)
    ));

    estados.put(TipoEstadoActividad.CONFIRMADA, new EstadoActividad(
        TipoEstadoActividad.CONFIRMADA,
        List.of(TipoEstadoActividad.FINALIZADA, TipoEstadoActividad.CANCELADA)
    ));

    estados.put(TipoEstadoActividad.REPROGRAMADA, new EstadoActividad(
        TipoEstadoActividad.REPROGRAMADA,
        List.of(TipoEstadoActividad.CONFIRMADA, TipoEstadoActividad.CANCELADA, TipoEstadoActividad.REPROGRAMADA)
    ));

    estados.put(TipoEstadoActividad.CANCELADA, new EstadoActividad(
        TipoEstadoActividad.CANCELADA,
        List.of()
    ));

    estados.put(TipoEstadoActividad.FINALIZADA, new EstadoActividad(
        TipoEstadoActividad.FINALIZADA,
        List.of()
    ));
  }

  public static EstadoActividad getEstado(TipoEstadoActividad tipo)
  {
    if (tipo == null)
      throw new IllegalArgumentException("El tipo de estado no puede ser nulo");

    return estados.get(tipo);
  }
}
