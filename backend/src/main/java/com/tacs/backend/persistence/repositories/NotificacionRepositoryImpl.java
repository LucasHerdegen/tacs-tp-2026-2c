package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.notificacion.Notificacion;
import com.tacs.backend.persistence.entities.NotificacionEntity;
import com.tacs.backend.persistence.mappers.NotificacionMapper;
import com.tacs.backend.repositories.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificacionRepositoryImpl implements NotificacionRepository
{
  private final NotificacionMongoRepository mongoRepository;
  private final NotificacionMapper mapper;

  @Override
  public Page<Notificacion> buscarPorUsuario(String usuarioId, Boolean leida, Pageable pageable)
  {
    Page<NotificacionEntity> entities = leida == null
        ? mongoRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId, pageable)
        : mongoRepository.findByUsuarioIdAndLeidaOrderByFechaCreacionDesc(usuarioId, leida, pageable);
    return entities.map(mapper::toDomain);
  }

  @Override
  public long contarNoLeidas(String usuarioId)
  {
    return mongoRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
  }

  @Override
  public List<Notificacion> findNoLeidasByUsuario(String usuarioId)
  {
    return mongoRepository.findByUsuarioIdAndLeidaFalse(usuarioId).stream().map(mapper::toDomain).toList();
  }

  @Override
  public Notificacion save(Notificacion notificacion)
  {
    return mapper.toDomain(mongoRepository.save(mapper.toEntity(notificacion)));
  }

  @Override
  public List<Notificacion> saveAll(List<Notificacion> notificaciones)
  {
    List<NotificacionEntity> entities = notificaciones.stream().map(mapper::toEntity).toList();
    return mongoRepository.saveAll(entities).stream().map(mapper::toDomain).toList();
  }

  @Override
  public Optional<Notificacion> findById(String id)
  {
    return mongoRepository.findById(id).map(mapper::toDomain);
  }
}
