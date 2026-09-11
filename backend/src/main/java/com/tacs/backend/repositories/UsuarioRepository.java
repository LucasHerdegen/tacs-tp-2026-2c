package com.tacs.backend.repositories;

import com.tacs.backend.domain.usuario.Usuario;

import java.util.Optional;

public interface UsuarioRepository
{
  Optional<Usuario> findByUsername(String username);

  boolean existsByUsername(String username);

  Usuario save(Usuario usuario);

  Optional<Usuario> findById(Long id);

  boolean existsById(Long id);
}
