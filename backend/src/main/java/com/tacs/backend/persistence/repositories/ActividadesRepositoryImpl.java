package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.persistence.mappers.ActividadMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ActividadesRepositoryImpl implements ActividadesRepository
{
  private final ActividadesJpaRepository jpaRepository;
  private final ActividadMapper mapper;

  @Override
  public List<Actividad> findByOrganizadorId(Long organizadorId)
  {
    return jpaRepository.findByOrganizadorId(organizadorId).stream().map(mapper::toDomain).collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByOrganizadorIdAndEstado(Long organizadorId, TipoEstadoActividad estado)
  {
    return jpaRepository.findByOrganizadorIdAndEstado(organizadorId, estado).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByParticipantesId(Long usuarioId)
  {
    return jpaRepository.findByParticipantesId(usuarioId).stream().map(mapper::toDomain).collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByParticipantesIdAndEstado(Long usuarioId, TipoEstadoActividad estado)
  {
    return jpaRepository.findByParticipantesIdAndEstado(usuarioId, estado).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public long countByEstado(TipoEstadoActividad estado)
  {
    return jpaRepository.countByEstado(estado);
  }

  @Override
  public List<Actividad> findCandidatasParaChequeoClima()
  {
    return jpaRepository.findCandidatasParaChequeoClima().stream().map(mapper::toDomain).collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByEstadoAndRecordatorioEnviadoFalse(TipoEstadoActividad estado)
  {
    return jpaRepository.findByEstadoAndRecordatorioEnviadoFalse(estado).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findCandidatasParaRecordatorio()
  {
    return jpaRepository.findCandidatasParaRecordatorio().stream().map(mapper::toDomain).collect(Collectors.toList());
  }

  @Override
  public Actividad save(Actividad actividad)
  {
    return mapper.toDomain(jpaRepository.save(mapper.toEntity(actividad)));
  }

  @Override
  public Optional<Actividad> findById(Long id)
  {
    return jpaRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  public List<Actividad> findAll()
  {
    return jpaRepository.findAll().stream().map(mapper::toDomain).collect(Collectors.toList());
  }

  @Override
  public long count()
  {
    return jpaRepository.count();
  }
}
