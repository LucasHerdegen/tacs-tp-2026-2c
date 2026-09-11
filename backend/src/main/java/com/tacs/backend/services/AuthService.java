package com.tacs.backend.services;

import com.tacs.backend.dtos.auth.LoginRequest;
import com.tacs.backend.dtos.auth.LoginResponse;
import com.tacs.backend.dtos.auth.RegistroRequest;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.domain.usuario.MedioContacto;

public interface AuthService
{
  UsuarioDto registrar(RegistroRequest request);

  LoginResponse login(LoginRequest request);

  UsuarioDto buscarPorUsername(String username);

  UsuarioDto actualizarRol(Long usuarioId, TipoRol rol);

  UsuarioDto obtenerUsuario(Long usuarioId);

  UsuarioDto actualizarContacto(Long usuarioId, MedioContacto medioContacto);
}
