package com.tacs.backend.persistence.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RangoReprogramacionEntity
{
  private int dias;
  private int horaInicio;
  private int horaFinal;
}
