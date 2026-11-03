package com.tacs.backend.domain.usuario;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Usuario
{
  private String id;
  private String username;
  private String password;
  private TipoRol rol;
  private MedioContacto medioContacto;
  private TipoProveedorClima proveedorClima = TipoProveedorClima.WEATHER_API;

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
    this.proveedorClima = TipoProveedorClima.WEATHER_API;
  }

  public TipoProveedorClima getProveedorClimaEfectivo()
  {
    return proveedorClima != null ? proveedorClima : TipoProveedorClima.WEATHER_API;
  }

  public void cambiarProveedorClima(TipoProveedorClima nuevoProveedor)
  {
    this.proveedorClima = nuevoProveedor != null ? nuevoProveedor : TipoProveedorClima.WEATHER_API;
  }
}
