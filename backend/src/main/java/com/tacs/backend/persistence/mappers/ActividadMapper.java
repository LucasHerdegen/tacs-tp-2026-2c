package com.tacs.backend.persistence.mappers;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.persistence.entities.ActividadEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.stream.Collectors;

@Component
public class ActividadMapper
{
  private final UsuarioMapper usuarioMapper;
  private final com.tacs.backend.persistence.repositories.UsuarioJpaRepository usuarioJpaRepository;

  public ActividadMapper(UsuarioMapper usuarioMapper,
                         com.tacs.backend.persistence.repositories.UsuarioJpaRepository usuarioJpaRepository)
  {
    this.usuarioMapper = usuarioMapper;
    this.usuarioJpaRepository = usuarioJpaRepository;
  }

  public Actividad toDomain(ActividadEntity entity)
  {
    if (entity == null) return null;
    Actividad domain = new Actividad();
    domain.setId(entity.getId());
    domain.setTitulo(entity.getTitulo());
    domain.setDescripcion(entity.getDescripcion());
    domain.setTipo(entity.getTipo());
    domain.setUbicacion(entity.getUbicacion());
    domain.setFechaCreacion(entity.getFechaCreacion());
    domain.setFechaRealizacion(entity.getFechaRealizacion());
    domain.setDuracionEstimada(entity.getDuracionEstimada());
    domain.setMinimoParticipantes(entity.getMinimoParticipantes());
    domain.setMaximoParticipantes(entity.getMaximoParticipantes());
    domain.setRecordatorioEnviado(entity.isRecordatorioEnviado());
    domain.setOrganizador(usuarioMapper.toDomain(entity.getOrganizador()));
    if (entity.getParticipantes() != null)
    {
      domain.setParticipantes(
          entity.getParticipantes().stream().map(usuarioMapper::toDomain).collect(Collectors.toList()));
    } else
    {
      domain.setParticipantes(new ArrayList<>());
    }
    domain.setHorasAnticipacion(entity.getHorasAnticipacion());
    domain.setRangoReprogramacion(entity.getRangoReprogramacion());

    if (entity.getCambiosFecha() != null)
    {
      domain.setCambiosFecha(new ArrayList<>(entity.getCambiosFecha()));
    } else
    {
      domain.setCambiosFecha(new ArrayList<>());
    }
    domain.setEstado(entity.getEstado());
    domain.setReglasClima(entity.getReglasClima());
    return domain;
  }

  public ActividadEntity toEntity(Actividad domain)
  {
    if (domain == null) return null;
    ActividadEntity entity = new ActividadEntity();
    entity.setId(domain.getId());
    entity.setTitulo(domain.getTitulo());
    entity.setDescripcion(domain.getDescripcion());
    entity.setTipo(domain.getTipo());
    entity.setUbicacion(domain.getUbicacion());
    entity.setFechaCreacion(domain.getFechaCreacion());
    entity.setFechaRealizacion(domain.getFechaRealizacion());
    entity.setDuracionEstimada(domain.getDuracionEstimada());
    entity.setMinimoParticipantes(domain.getMinimoParticipantes());
    entity.setMaximoParticipantes(domain.getMaximoParticipantes());
    entity.setRecordatorioEnviado(domain.isRecordatorioEnviado());

    if (domain.getOrganizador() != null && domain.getOrganizador().getId() != null)
    {
      entity.setOrganizador(usuarioJpaRepository.getReferenceById(domain.getOrganizador().getId()));
    } else
    {
      entity.setOrganizador(usuarioMapper.toEntity(domain.getOrganizador()));
    }

    if (domain.getParticipantes() != null)
    {
      entity.setParticipantes(domain.getParticipantes().stream().map(p -> {
        if (p.getId() != null)
        {
          return usuarioJpaRepository.getReferenceById(p.getId());
        }
        return usuarioMapper.toEntity(p);
      }).collect(Collectors.toList()));
    } else
    {
      entity.setParticipantes(new ArrayList<>());
    }
    entity.setHorasAnticipacion(domain.getHorasAnticipacion());
    entity.setRangoReprogramacion(domain.getRangoReprogramacion());

    if (domain.getCambiosFecha() != null)
    {
      entity.setCambiosFecha(new ArrayList<>(domain.getCambiosFecha()));
    } else
    {
      entity.setCambiosFecha(new ArrayList<>());
    }
    entity.setEstado(domain.getEstado());
    entity.setReglasClima(domain.getReglasClima());
    return entity;
  }
}
