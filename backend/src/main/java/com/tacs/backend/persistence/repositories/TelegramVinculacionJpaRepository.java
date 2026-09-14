package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.TelegramVinculacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramVinculacionJpaRepository extends JpaRepository<TelegramVinculacionEntity, Long>
{
  Optional<TelegramVinculacionEntity> findByToken(String token);

  List<TelegramVinculacionEntity> findByUsuarioIdAndUsadoFalse(Long usuarioId);
}
