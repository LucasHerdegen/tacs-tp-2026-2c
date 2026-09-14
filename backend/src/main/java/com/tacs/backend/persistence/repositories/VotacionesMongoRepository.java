package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.VotacionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VotacionesMongoRepository extends MongoRepository<VotacionEntity, String>
{
  @Query("{ 'abierta': ?0, 'actividad': { $in: ?1 } }")
  List<VotacionEntity> findByAbiertaAndActividadIdIn(boolean abierta, List<org.bson.types.ObjectId> actividadIds);

  @Query("{ 'abierta': true, 'actividad': ?0 }")
  Optional<VotacionEntity> findByAbiertaTrueAndActividadId(org.bson.types.ObjectId actividadId);

  List<VotacionEntity> findByAbiertaTrueAndFechaLimiteBefore(LocalDateTime ahora);

  List<VotacionEntity> findByAbiertaTrue();
}
