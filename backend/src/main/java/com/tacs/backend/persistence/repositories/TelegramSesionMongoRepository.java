package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramSesionMongoRepository extends MongoRepository<TelegramSesionEntity, Long>
{
}
