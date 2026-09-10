package com.tacs.backend.persistence.mappers;

import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.persistence.entities.AlternativaEntity;
import org.springframework.stereotype.Component;

@Component
public class AlternativaMapper
{

  public Alternativa toDomain(AlternativaEntity entity)
  {
    if (entity == null) return null;
    Alternativa domain = new Alternativa();
    domain.setId(entity.getId());
    domain.setFecha(entity.getFecha());
    domain.setClima(entity.getClima());
    domain.setNumeroAltenativa(entity.getNumeroAltenativa());
    return domain;
  }

  public AlternativaEntity toEntity(Alternativa domain)
  {
    if (domain == null) return null;
    AlternativaEntity entity = new AlternativaEntity();
    entity.setId(domain.getId());
    entity.setFecha(domain.getFecha());
    entity.setClima(domain.getClima());
    entity.setNumeroAltenativa(domain.getNumeroAltenativa());
    return entity;
  }
}
