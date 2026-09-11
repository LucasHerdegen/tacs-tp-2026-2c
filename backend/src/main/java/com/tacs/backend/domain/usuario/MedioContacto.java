package com.tacs.backend.domain.usuario;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedioContacto
{
  private String valor;
  private TipoMedioContacto tipo;
}
