package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.persistence.mappers.ActividadMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Set;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ActividadesRepositoryImpl implements ActividadesRepository
{
  private final ActividadesMongoRepository mongoRepository;
  private final VotacionesMongoRepository votacionesMongoRepository;
  private final ActividadMapper mapper;

  @Override
  public List<Actividad> findByOrganizadorId(String organizadorId)
  {
    return mongoRepository.findByOrganizadorId(organizadorId).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByOrganizadorIdAndEstado(String organizadorId, TipoEstadoActividad estado)
  {
    return mongoRepository.findByOrganizadorIdAndEstado(organizadorId, estado).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByParticipantesId(String usuarioId)
  {
    return mongoRepository.findByParticipantesId(usuarioId).stream().map(mapper::toDomain).collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByParticipantesIdAndEstado(String usuarioId, TipoEstadoActividad estado)
  {
    return mongoRepository.findByParticipantesIdAndEstado(usuarioId, estado).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByOrganizadorIdOrParticipantesId(String usuarioId)
  {
    return mongoRepository.findByOrganizadorIdOrParticipantesId(usuarioId, usuarioId).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByOrganizadorIdOrParticipantesIdAndEstado(String usuarioId, TipoEstadoActividad estado)
  {
    return mongoRepository.findByOrganizadorIdAndEstadoOrParticipantesIdAndEstado(usuarioId, estado, usuarioId, estado).stream()
        .map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public long countByEstado(TipoEstadoActividad estado)
  {
    return mongoRepository.countByEstado(estado);
  }

  @Override
  public List<Actividad> findCandidatasParaChequeoClima()
  {
    Set<String> actividadesConVotacionAbierta = votacionesMongoRepository.findByAbiertaTrue().stream()
        .map(v -> v.getActividad().getId())
        .collect(Collectors.toSet());

    return mongoRepository.findActividadesActivasFuturasConClima(java.time.LocalDateTime.now()).stream()
        .filter(a -> !actividadesConVotacionAbierta.contains(a.getId()))
        .map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findByEstadoAndRecordatorioEnviadoFalse(TipoEstadoActividad estado)
  {
    return mongoRepository.findByEstadoAndRecordatorioEnviadoFalse(estado).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Actividad> findCandidatasParaRecordatorio()
  {
    return mongoRepository.findCandidatasParaRecordatorio(java.time.LocalDateTime.now()).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public Actividad save(Actividad actividad)
  {
    return mapper.toDomain(mongoRepository.save(mapper.toEntity(actividad)));
  }

  @Override
  public Optional<Actividad> findById(String id)
  {
    return mongoRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  public List<Actividad> findAll()
  {
    return mongoRepository.findAll().stream().map(mapper::toDomain).collect(Collectors.toList());
  }

  @Override
  public long count()
  {
    return mongoRepository.count();
  }
}
