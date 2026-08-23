package com.tacs.backend.domain.actividad;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RangoReprogramacion
{
  private int dias;
  private int horaInicio;
  private int horaFinal;
}
