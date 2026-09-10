package com.tacs.backend.domain.usuario;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Usuario
{
  private Long id;
  private String username;
  private String password;
  private TipoRol rol;
  private MedioContacto medioContacto;

  @Override
  public boolean equals(Object o)
  {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Usuario usuario = (Usuario) o;
    return id != null && id.equals(usuario.id);
  }

  @Override
  public int hashCode()
  {
    return getClass().hashCode();
  }

  public Usuario(String username, String password, TipoRol rol)
  {
    this.username = username;
    this.password = password;
    this.rol = rol;
  }
}
