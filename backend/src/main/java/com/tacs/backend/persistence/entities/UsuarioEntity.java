package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.usuario.TipoRol;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioEntity
{
  @Id
  private String id;

  private String username;

  private String password;

  private TipoRol rol;

  private MedioContactoEntity medioContacto;
}
