package com.tacs.backend.persistence.repositories;

import com.tacs.backend.persistence.entities.TelegramUpdateOffsetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramUpdateOffsetJpaRepository extends JpaRepository<TelegramUpdateOffsetEntity, Long>
{
}
