package com.tacs.backend.domain.usuario;

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
public class MedioContacto
{
  private String valor;

  @Enumerated(EnumType.STRING)
  private TipoMedioContacto tipo;
}
