package com.tacs.backend.persistence.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VotoEntity
{
    private String id;

      private AlternativaEntity alternativa;

      private UsuarioEntity usuario;
}
