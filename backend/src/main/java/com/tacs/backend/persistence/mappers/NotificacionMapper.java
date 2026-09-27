package com.tacs.backend.persistence.mappers;

import com.tacs.backend.domain.notificacion.Notificacion;
import com.tacs.backend.persistence.entities.NotificacionEntity;
import org.springframework.stereotype.Component;

@Component
public class NotificacionMapper
{
  public Notificacion toDomain(NotificacionEntity entity)
  {
    if (entity == null) return null;
    Notificacion domain = new Notificacion();
    domain.setId(entity.getId());
    domain.setVersion(entity.getVersion());
    domain.setUsuarioId(entity.getUsuarioId());
    domain.setContenido(entity.getContenido());
    domain.setTipo(entity.getTipo());
    domain.setActividadId(entity.getActividadId());
    domain.setVotacionId(entity.getVotacionId());
    domain.setLeida(entity.isLeida());
    domain.setFechaCreacion(entity.getFechaCreacion());
    domain.setFechaLectura(entity.getFechaLectura());
    return domain;
  }

  public NotificacionEntity toEntity(Notificacion domain)
  {
    if (domain == null) return null;
    NotificacionEntity entity = new NotificacionEntity();
    entity.setId(domain.getId());
    entity.setVersion(domain.getVersion());
    entity.setUsuarioId(domain.getUsuarioId());
    entity.setContenido(domain.getContenido());
    entity.setTipo(domain.getTipo());
    entity.setActividadId(domain.getActividadId());
    entity.setVotacionId(domain.getVotacionId());
    entity.setLeida(domain.isLeida());
    entity.setFechaCreacion(domain.getFechaCreacion());
    entity.setFechaLectura(domain.getFechaLectura());
    return entity;
  }
}
