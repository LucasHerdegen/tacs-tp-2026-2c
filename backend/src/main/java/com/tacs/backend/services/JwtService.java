package com.tacs.backend.services;

import com.tacs.backend.domain.usuario.Usuario;

public interface JwtService
{
  String generarToken(Usuario usuario);

  long getExpirationSeconds();
}
