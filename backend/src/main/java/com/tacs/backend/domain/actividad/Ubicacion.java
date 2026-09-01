package com.tacs.backend.domain.actividad;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Ubicacion
{
  @NotBlank(message = "El barrio es requerido")
  private String barrio;
  private double latitud;
  private double longitud;
}
