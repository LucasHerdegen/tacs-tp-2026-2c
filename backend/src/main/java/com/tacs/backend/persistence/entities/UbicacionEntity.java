package com.tacs.backend.persistence.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UbicacionEntity
{
  private String barrio;
  private Double latitud;
  private Double longitud;
}
