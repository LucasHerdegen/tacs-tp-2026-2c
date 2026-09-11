package com.tacs.backend.persistence.mappers;

import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.persistence.entities.VotacionEntity;
import com.tacs.backend.persistence.repositories.ActividadesJpaRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.stream.Collectors;

@Component
public class VotacionMapperEntity
{
  private final ActividadMapper actividadMapper;
  private final AlternativaMapper alternativaMapper;
  private final VotoMapper votoMapper;
  private final ActividadesJpaRepository actividadJpaRepository;

  public VotacionMapperEntity(ActividadMapper actividadMapper, AlternativaMapper alternativaMapper,
                              VotoMapper votoMapper,
                              ActividadesJpaRepository actividadJpaRepository)
  {
    this.actividadMapper = actividadMapper;
    this.alternativaMapper = alternativaMapper;
    this.votoMapper = votoMapper;
    this.actividadJpaRepository = actividadJpaRepository;
  }

  public Votacion toDomain(VotacionEntity entity)
  {
    if (entity == null) return null;
    Votacion domain = new Votacion();
    domain.setId(entity.getId());
    domain.setFechaApertura(entity.getFechaApertura());
    domain.setFechaCierre(entity.getFechaCierre());
    domain.setAbierta(entity.isAbierta());
    domain.setFechaLimite(entity.getFechaLimite());
    domain.setActividad(actividadMapper.toDomain(entity.getActividad()));

    if (entity.getAlternativas() != null)
    {
      domain.setAlternativas(
          entity.getAlternativas().stream().map(alternativaMapper::toDomain).collect(Collectors.toList()));
    } else
    {
      domain.setAlternativas(new ArrayList<>());
    }

    if (entity.getVotos() != null)
    {
      domain.setVotos(entity.getVotos().stream().map(votoMapper::toDomain).collect(Collectors.toList()));
    } else
    {
      domain.setVotos(new ArrayList<>());
    }

    domain.setAlternativaGanadora(alternativaMapper.toDomain(entity.getAlternativaGanadora()));
    domain.setQuorumMinimo(entity.getQuorumMinimo());

    return domain;
  }

  public VotacionEntity toEntity(Votacion domain)
  {
    if (domain == null) return null;
    VotacionEntity entity = new VotacionEntity();
    entity.setId(domain.getId());
    entity.setFechaApertura(domain.getFechaApertura());
    entity.setFechaCierre(domain.getFechaCierre());
    entity.setAbierta(domain.isAbierta());
    entity.setFechaLimite(domain.getFechaLimite());

    if (domain.getActividad() != null && domain.getActividad().getId() != null)
    {
      entity.setActividad(actividadJpaRepository.getReferenceById(domain.getActividad().getId()));
    } else
    {
      entity.setActividad(actividadMapper.toEntity(domain.getActividad()));
    }

    if (domain.getAlternativas() != null)
    {
      entity.setAlternativas(
          domain.getAlternativas().stream().map(alternativaMapper::toEntity).collect(Collectors.toList()));
    } else
    {
      entity.setAlternativas(new ArrayList<>());
    }

    if (domain.getVotos() != null)
    {
      entity.setVotos(domain.getVotos().stream().map(votoMapper::toEntity).collect(Collectors.toList()));
    } else
    {
      entity.setVotos(new ArrayList<>());
    }

    entity.setAlternativaGanadora(alternativaMapper.toEntity(domain.getAlternativaGanadora()));
    entity.setQuorumMinimo(domain.getQuorumMinimo());

    return entity;
  }
}
