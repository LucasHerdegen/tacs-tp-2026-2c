package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.TelegramVinculacionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramVinculacionMongoRepository extends MongoRepository<TelegramVinculacionEntity, String>
{
  Optional<TelegramVinculacionEntity> findByToken(String token);

  List<TelegramVinculacionEntity> findByUsuarioIdAndUsadoFalse(String usuarioId);
}
