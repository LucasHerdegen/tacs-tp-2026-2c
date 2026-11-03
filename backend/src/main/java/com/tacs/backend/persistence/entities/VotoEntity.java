package com.tacs.backend.persistence.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Getter
@Setter
@NoArgsConstructor
public class VotoEntity
{
  private String id;

  private AlternativaEntity alternativa;

  @DocumentReference
  private UsuarioEntity usuario;
}
