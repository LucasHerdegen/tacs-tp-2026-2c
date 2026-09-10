package com.tacs.backend.dtos.actividades;

import jakarta.validation.constraints.NotBlank;
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
  @NotBlank(message = "El barrio es requerido")
  private String barrio;
  private Double latitud;
  private Double longitud;
}
