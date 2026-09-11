package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.usuario.TipoMedioContacto;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedioContactoEntity
{
  @Enumerated(EnumType.STRING)
  private TipoMedioContacto tipo;
  private String valor;
}
