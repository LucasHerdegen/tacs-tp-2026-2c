package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.persistence.mappers.UsuarioMapper;
import com.tacs.backend.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UsuarioRepositoryImpl implements UsuarioRepository
{
  private final UsuarioJpaRepository jpaRepository;
  private final UsuarioMapper mapper;

  @Override
  public Optional<Usuario> findByUsername(String username)
  {
    return jpaRepository.findByUsername(username).map(mapper::toDomain);
  }

  @Override
  public boolean existsByUsername(String username)
  {
    return jpaRepository.existsByUsername(username);
  }

  @Override
  public Usuario save(Usuario usuario)
  {
    return mapper.toDomain(jpaRepository.save(mapper.toEntity(usuario)));
  }

  @Override
  public Optional<Usuario> findById(Long id)
  {
    return jpaRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  public boolean existsById(Long id)
  {
    return jpaRepository.existsById(id);
  }
}
