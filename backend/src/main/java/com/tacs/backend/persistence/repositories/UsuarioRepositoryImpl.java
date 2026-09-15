package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.usuario.MedioContacto;
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
  private final UsuarioMongoRepository mongoRepository;
  private final UsuarioMapper mapper;

  @Override
  public Optional<Usuario> findByUsername(String username)
  {
    return mongoRepository.findByUsername(username).map(mapper::toDomain);
  }

  @Override
  public boolean existsByUsername(String username)
  {
    return mongoRepository.existsByUsername(username);
  }

  @Override
  public Usuario save(Usuario usuario)
  {
    return mapper.toDomain(mongoRepository.save(mapper.toEntity(usuario)));
  }

  @Override
  public Optional<Usuario> findById(String id)
  {
    return mongoRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  public boolean existsById(String id)
  {
    return mongoRepository.existsById(id);
  }

  @Override
  public Optional<Usuario> findByMedioContacto(MedioContacto medioContacto)
  {
    return mongoRepository
        .findByMedioContacto_ValorAndMedioContacto_Tipo(medioContacto.getValor(), medioContacto.getTipo())
        .map(mapper::toDomain);
  }
}
