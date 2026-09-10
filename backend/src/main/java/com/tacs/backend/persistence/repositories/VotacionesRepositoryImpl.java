package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.persistence.mappers.VotacionMapperEntity;
import com.tacs.backend.repositories.VotacionesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class VotacionesRepositoryImpl implements VotacionesRepository
{

  private final VotacionesJpaRepository jpaRepository;
  private final VotacionMapperEntity mapper;

  @Override
  public List<Votacion> findByAbiertaAndActividadOrganizadorId(boolean abierta, Long organizadorId)
  {
    return jpaRepository.findByAbiertaAndActividadOrganizadorId(abierta, organizadorId).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Votacion> findByAbiertaAndActividadParticipantesId(boolean abierta, Long usuarioId)
  {
    return jpaRepository.findByAbiertaAndActividadParticipantesId(abierta, usuarioId).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<Votacion> findByAbiertaTrueAndActividadId(Long actividadId)
  {
    return jpaRepository.findByAbiertaTrueAndActividadId(actividadId).map(mapper::toDomain);
  }

  @Override
  public List<Votacion> findByAbiertaTrueAndFechaLimiteBefore(LocalDateTime ahora)
  {
    return jpaRepository.findByAbiertaTrueAndFechaLimiteBefore(ahora).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Votacion> findByAbiertaYUsuarioInvolucrado(boolean abierta, Long usuarioId)
  {
    return jpaRepository.findByAbiertaYUsuarioInvolucrado(abierta, usuarioId).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public Votacion save(Votacion votacion)
  {
    return mapper.toDomain(jpaRepository.save(mapper.toEntity(votacion)));
  }

  @Override
  public Optional<Votacion> findById(Long id)
  {
    return jpaRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  public void delete(Votacion votacion)
  {
    jpaRepository.delete(mapper.toEntity(votacion));
  }
}
