package com.tacs.backend.persistence.mappers;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.persistence.entities.ActividadEntity;
import com.tacs.backend.persistence.entities.ReglasClimaEntity;
import com.tacs.backend.persistence.entities.CambioFechaEntity;
import com.tacs.backend.persistence.entities.RangoReprogramacionEntity;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.domain.actividad.CambioFecha;
import com.tacs.backend.domain.actividad.RangoReprogramacion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.stream.Collectors;

import com.tacs.backend.persistence.repositories.UsuarioJpaRepository;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.persistence.entities.UbicacionEntity;

@Component
public class ActividadMapper
{
  private final UsuarioMapper usuarioMapper;
  private final UsuarioJpaRepository usuarioJpaRepository;

  public ActividadMapper(UsuarioMapper usuarioMapper,
                         UsuarioJpaRepository usuarioJpaRepository)
  {
    this.usuarioMapper = usuarioMapper;
    this.usuarioJpaRepository = usuarioJpaRepository;
  }

  public Actividad toDomain(ActividadEntity entity)
  {
    if (entity == null) return null;
    Actividad domain = new Actividad();
    domain.setId(entity.getId());
    domain.setVersion(entity.getVersion());
    domain.setTitulo(entity.getTitulo());
    domain.setDescripcion(entity.getDescripcion());
    domain.setTipo(entity.getTipo());
    domain.setUbicacion(mapUbicacionToDomain(entity.getUbicacion()));
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
    if (entity.getRangoReprogramacion() != null)
    {
      domain.setRangoReprogramacion(
          new RangoReprogramacion(entity.getRangoReprogramacion().getDias(),
              entity.getRangoReprogramacion().getHoraInicio(), entity.getRangoReprogramacion().getHoraFinal()));
    }

    if (entity.getCambiosFecha() != null)
    {
      domain.setCambiosFecha(entity.getCambiosFecha().stream().map(
              c -> new CambioFecha(c.getFecha(), c.getFechaAntigua(), c.getFechaNueva()))
          .collect(java.util.stream.Collectors.toList()));
    } else
    {
      domain.setCambiosFecha(new ArrayList<>());
    }
    domain.setEstado(entity.getEstado());
    if (entity.getReglasClima() != null)
    {
      domain.setReglasClima(
          new ReglasClima(entity.getReglasClima().getMaxProbabilidadLluvia(),
              entity.getReglasClima().getMinTemperatura(), entity.getReglasClima().getMaxTemperatura(),
              entity.getReglasClima().getMaxViento()));
    }
    return domain;
  }

  public ActividadEntity toEntity(Actividad domain)
  {
    if (domain == null) return null;
    ActividadEntity entity = new ActividadEntity();
    entity.setId(domain.getId());
    entity.setVersion(domain.getVersion());
    entity.setTitulo(domain.getTitulo());
    entity.setDescripcion(domain.getDescripcion());
    entity.setTipo(domain.getTipo());
    entity.setUbicacion(mapUbicacionToEntity(domain.getUbicacion()));
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
    if (domain.getRangoReprogramacion() != null)
    {
      entity.setRangoReprogramacion(
          new RangoReprogramacionEntity(domain.getRangoReprogramacion().getDias(),
              domain.getRangoReprogramacion().getHoraInicio(), domain.getRangoReprogramacion().getHoraFinal()));
    }

    if (domain.getCambiosFecha() != null)
    {
      entity.setCambiosFecha(domain.getCambiosFecha().stream().map(
          c -> new CambioFechaEntity(c.getFecha(), c.getFechaAntigua(),
              c.getFechaNueva())).collect(java.util.stream.Collectors.toList()));
    } else
    {
      entity.setCambiosFecha(new ArrayList<>());
    }
    entity.setEstado(domain.getEstado());
    if (domain.getReglasClima() != null)
    {
      entity.setReglasClima(new ReglasClimaEntity(
          domain.getReglasClima().getMaxProbabilidadLluvia(), domain.getReglasClima().getMinTemperatura(),
          domain.getReglasClima().getMaxTemperatura(), domain.getReglasClima().getMaxViento()));
    }
    return entity;
  }

  private Ubicacion mapUbicacionToDomain(UbicacionEntity entity)
  {
    if (entity == null) return null;
    return new Ubicacion(entity.getBarrio(), entity.getLatitud(), entity.getLongitud());
  }

  private UbicacionEntity mapUbicacionToEntity(Ubicacion domain)
  {
    if (domain == null) return null;
    return new UbicacionEntity(domain.getBarrio(), domain.getLatitud(), domain.getLongitud());
  }
}
