package com.tacs.backend.dtos.actividades;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UbicacionDto
{
  private String ciudad;
  private Double latitud;
  private Double longitud;

  @AssertTrue(message = "Debe enviar la ciudad o las coordenadas (latitud y longitud)")
  public boolean isUbicacionValida()
  {
    boolean tieneCiudad = ciudad != null && !ciudad.trim().isEmpty();
    boolean tieneCoordenadas = latitud != null && longitud != null;
    return tieneCiudad || tieneCoordenadas;
  }
}
