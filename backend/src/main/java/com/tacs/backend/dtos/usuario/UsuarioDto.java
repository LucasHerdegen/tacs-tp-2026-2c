package com.tacs.backend.dtos.usuario;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;

public record UsuarioDto(
    String id,
    String username,
    TipoRol rol,
    MedioContacto medioContacto,
    TipoProveedorClima proveedorClima)
{
  public UsuarioDto(String id, String username, TipoRol rol, MedioContacto medioContacto)
  {
    this(id, username, rol, medioContacto, TipoProveedorClima.WEATHER_API);
  }
}
