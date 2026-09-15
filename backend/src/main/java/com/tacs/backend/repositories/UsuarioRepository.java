package com.tacs.backend.repositories;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.Usuario;

import java.util.Optional;

public interface UsuarioRepository
{
  Optional<Usuario> findByUsername(String username);

  boolean existsByUsername(String username);

  Usuario save(Usuario usuario);

  Optional<Usuario> findById(String id);

  boolean existsById(String id);

  Optional<Usuario> findByMedioContacto(MedioContacto medioContacto);

  java.util.List<Usuario> findAll();

  void deleteAll();
}
