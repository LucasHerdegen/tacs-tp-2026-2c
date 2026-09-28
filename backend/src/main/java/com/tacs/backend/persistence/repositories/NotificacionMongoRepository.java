package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.NotificacionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionMongoRepository extends MongoRepository<NotificacionEntity, String>
{
  Page<NotificacionEntity> findByUsuarioIdOrderByFechaCreacionDesc(String usuarioId, Pageable pageable);

  Page<NotificacionEntity> findByUsuarioIdAndLeidaOrderByFechaCreacionDesc(String usuarioId, boolean leida, Pageable pageable);

  long countByUsuarioIdAndLeidaFalse(String usuarioId);

  List<NotificacionEntity> findByUsuarioIdAndLeidaFalse(String usuarioId);
}
