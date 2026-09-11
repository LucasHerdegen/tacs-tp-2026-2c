package com.tacs.backend.persistence.mappers;

import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.persistence.entities.UsuarioEntity;
import com.tacs.backend.persistence.entities.MedioContactoEntity;
import com.tacs.backend.domain.usuario.MedioContacto;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper
{

  public Usuario toDomain(UsuarioEntity entity)
  {
    if (entity == null) return null;
    Usuario domain = new Usuario();
    domain.setId(entity.getId());
    domain.setUsername(entity.getUsername());
    domain.setPassword(entity.getPassword());
    domain.setRol(entity.getRol());
    if (entity.getMedioContacto() != null)
    {
      domain.setMedioContacto(new MedioContacto(entity.getMedioContacto().getValor(),
          entity.getMedioContacto().getTipo()));
    }
    return domain;
  }

  public UsuarioEntity toEntity(Usuario domain)
  {
    if (domain == null) return null;
    UsuarioEntity entity = new UsuarioEntity();
    entity.setId(domain.getId());
    entity.setUsername(domain.getUsername());
    entity.setPassword(domain.getPassword());
    entity.setRol(domain.getRol());
    if (domain.getMedioContacto() != null)
    {
      entity.setMedioContacto(
          new MedioContactoEntity(domain.getMedioContacto().getTipo(),
              domain.getMedioContacto().getValor()));
    }
    return entity;
  }
}
