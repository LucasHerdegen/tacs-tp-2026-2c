package com.tacs.backend.persistence.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReglasClimaEntity
{
  private Double maxProbabilidadLluvia;
  private Double minTemperatura;
  private Double maxTemperatura;
  private Double maxViento;
}
