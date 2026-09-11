package com.tacs.backend.persistence.mappers;

import com.tacs.backend.domain.votacion.Voto;
import com.tacs.backend.persistence.entities.VotoEntity;
import com.tacs.backend.persistence.repositories.UsuarioJpaRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class VotoMapper
{
  private final UsuarioMapper usuarioMapper;
  private final AlternativaMapper alternativaMapper;
  private final UsuarioJpaRepository usuarioJpaRepository;

  public VotoMapper(UsuarioMapper usuarioMapper, @Lazy AlternativaMapper alternativaMapper,
                    UsuarioJpaRepository usuarioJpaRepository)
  {
    this.usuarioMapper = usuarioMapper;
    this.alternativaMapper = alternativaMapper;
    this.usuarioJpaRepository = usuarioJpaRepository;
  }

  public Voto toDomain(VotoEntity entity)
  {
    if (entity == null) return null;
    Voto domain = new Voto();
    domain.setId(entity.getId());
    domain.setAlternativa(alternativaMapper.toDomain(entity.getAlternativa()));
    domain.setUsuario(usuarioMapper.toDomain(entity.getUsuario()));
    return domain;
  }

  public VotoEntity toEntity(Voto domain)
  {
    if (domain == null) return null;
    VotoEntity entity = new VotoEntity();
    entity.setId(domain.getId());
    entity.setAlternativa(alternativaMapper.toEntity(domain.getAlternativa()));
    if (domain.getUsuario() != null && domain.getUsuario().getId() != null)
    {
      entity.setUsuario(usuarioJpaRepository.getReferenceById(domain.getUsuario().getId()));
    } else
    {
      entity.setUsuario(usuarioMapper.toEntity(domain.getUsuario()));
    }
    return entity;
  }
}
