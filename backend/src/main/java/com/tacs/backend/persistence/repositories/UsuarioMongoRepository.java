package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.UsuarioEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioMongoRepository extends MongoRepository<UsuarioEntity, String>
{
  Optional<UsuarioEntity> findByUsername(String username);

  boolean existsByUsername(String username);
}
