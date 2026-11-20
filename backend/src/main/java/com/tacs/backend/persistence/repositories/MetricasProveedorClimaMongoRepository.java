package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.MetricasProveedorClimaEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetricasProveedorClimaMongoRepository extends MongoRepository<MetricasProveedorClimaEntity, String>
{
}
