package com.tacs.backend.persistence.entities;

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
public class UbicacionEntity
{
  private String barrio;
  private Double latitud;
  private Double longitud;
}
