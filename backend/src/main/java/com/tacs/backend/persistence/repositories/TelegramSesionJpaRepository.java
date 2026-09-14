package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramSesionJpaRepository extends JpaRepository<TelegramSesionEntity, Long>
{
}
