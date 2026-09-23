package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.persistence.mappers.ActividadMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.persistence.entities.ActividadEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.bson.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ActividadesRepositoryImpl implements ActividadesRepository
{
  private final ActividadesMongoRepository mongoRepository;
  private final VotacionesMongoRepository votacionesMongoRepository;
  private final ActividadMapper mapper;

  private final MongoTemplate mongoTemplate;

  @Override
  public Page<Actividad> buscarActividades(TipoActividad tipo, String busqueda, LocalDate fecha,
                                           TipoEstadoActividad estado, Boolean cupoDisponible, Pageable pageable)
  {
    Query query = new Query();

    if (tipo != null)
      query.addCriteria(Criteria.where("tipo").is(tipo));

    if (busqueda != null && !busqueda.isBlank())
    {
      Criteria orCriteria = new Criteria().orOperator(
          Criteria.where("titulo").regex(busqueda, "i"),
          Criteria.where("ubicacion.ciudad").regex(busqueda, "i")
      );
      query.addCriteria(orCriteria);
    }

    if (fecha != null)
    {
      LocalDateTime startOfDay = fecha.atStartOfDay();
      LocalDateTime endOfDay = fecha.atTime(23, 59, 59, 999999999);
      query.addCriteria(Criteria.where("fechaRealizacion").gte(startOfDay).lte(endOfDay));
    }

    if (estado != null)
      query.addCriteria(Criteria.where("estado").is(estado));

    if (Boolean.TRUE.equals(cupoDisponible))
    {
      query.addCriteria(new Criteria()
      {
        @Override
        public @NonNull Document getCriteriaObject()
        {
          return Document.parse("{ \"\": { \"\": [ { \"\": { \"\": [ \"\", [] ] } }, \"\" ] } }");
        }
      });
    }

    long total = mongoTemplate.count(query, ActividadEntity.class);
    query.with(pageable);
    List<ActividadEntity> entities = mongoTemplate.find(query, ActividadEntity.class);
    List<Actividad> domainList = entities.stream().map(mapper::toDomain).toList();

    return PageableExecutionUtils.getPage(domainList, pageable, () -> total);
  }


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
    return mongoRepository.findByOrganizadorIdAndEstadoOrParticipantesIdAndEstado(usuarioId, estado, usuarioId, estado)
        .stream()
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

    return mongoRepository.findActividadesActivasFuturasConClima(LocalDateTime.now()).stream()
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
    return mongoRepository.findCandidatasParaRecordatorio(LocalDateTime.now()).stream().map(mapper::toDomain)
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
  public List<Actividad> findCandidatasParaFinalizacion(LocalDateTime now)
  {
    return mongoRepository.findCandidatasParaFinalizacion(now).stream().map(mapper::toDomain)
        .collect(Collectors.toList());
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
