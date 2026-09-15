package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.TelegramUpdateOffsetEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramUpdateOffsetMongoRepository extends MongoRepository<TelegramUpdateOffsetEntity, Long>
{
}
