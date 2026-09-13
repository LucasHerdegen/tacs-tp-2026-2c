package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.usuario.TipoMedioContacto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedioContactoEntity
{
  private TipoMedioContacto tipo;
  private String valor;
}
