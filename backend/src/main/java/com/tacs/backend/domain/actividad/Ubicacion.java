package com.tacs.backend.domain.actividad;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@EqualsAndHashCode
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Ubicacion implements java.io.Serializable
{
  private String barrio;
  private Double latitud;
  private Double longitud;
}
