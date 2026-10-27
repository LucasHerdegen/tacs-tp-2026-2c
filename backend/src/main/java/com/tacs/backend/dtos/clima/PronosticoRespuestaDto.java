package com.tacs.backend.dtos.clima;

import com.tacs.backend.domain.clima.TipoProveedorClima;

public record PronosticoRespuestaDto(
    ClimaDto climaActual,
    ClimaDto pronosticoFuturo,
    TipoProveedorClima proveedorSeleccionado,
    TipoProveedorClima proveedorUtilizado,
    boolean fallbackActivo,
    boolean degradado,
    String mensajeEstado)
{
  public PronosticoRespuestaDto(ClimaDto climaActual, ClimaDto pronosticoFuturo)
  {
    this(
        climaActual,
        pronosticoFuturo,
        TipoProveedorClima.WEATHER_API,
        TipoProveedorClima.WEATHER_API,
        false,
        false,
        null);
  }
}
