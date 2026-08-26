package com.tacs.backend.services;

import com.tacs.backend.dtos.auth.LoginRequest;
import com.tacs.backend.dtos.auth.LoginResponse;
import com.tacs.backend.dtos.auth.RegistroRequest;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.dtos.usuario.UsuarioDto;

public interface AuthService
{
  UsuarioDto registrar(RegistroRequest request);

  LoginResponse login(LoginRequest request);

  UsuarioDto buscarPorUsername(String username);

  UsuarioDto actualizarRol(Long usuarioId, TipoRol rol);
}
