package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.persistence.entities.VotacionEntity;
import com.tacs.backend.persistence.mappers.AlternativaMapper;
import com.tacs.backend.persistence.mappers.VotacionMapperEntity;
import com.tacs.backend.repositories.VotacionesRepository;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class VotacionesRepositoryImpl implements VotacionesRepository
{

  private final VotacionesMongoRepository mongoRepository;
  private final ActividadesMongoRepository actividadesMongoRepository;
  private final VotacionMapperEntity mapper;
  private final AlternativaMapper alternativaMapper;
  private final MongoTemplate mongoTemplate;

  @Override
  public List<Votacion> findByAbiertaAndActividadOrganizadorId(boolean abierta, String organizadorId)
  {
    List<ObjectId> actividadIds = actividadesMongoRepository.findByOrganizadorId(organizadorId).stream()
        .map(a -> new ObjectId(a.getId()))
        .collect(Collectors.toList());
    return mongoRepository.findByAbiertaAndActividadIdIn(abierta, actividadIds).stream()
        .map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Votacion> findByAbiertaAndActividadParticipantesId(boolean abierta, String usuarioId)
  {
    List<ObjectId> actividadIds = actividadesMongoRepository.findByParticipantesId(usuarioId).stream()
        .map(a -> new ObjectId(a.getId()))
        .collect(Collectors.toList());
    return mongoRepository.findByAbiertaAndActividadIdIn(abierta, actividadIds).stream()
        .map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<Votacion> findByAbiertaTrueAndActividadId(String actividadId)
  {
    return mongoRepository.findByAbiertaTrueAndActividadId(new ObjectId(actividadId)).map(mapper::toDomain);
  }

  @Override
  public List<Votacion> findByAbiertaTrueAndFechaLimiteBefore(LocalDateTime ahora)
  {
    return mongoRepository.findByAbiertaTrueAndFechaLimiteBefore(ahora).stream()
        .map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Votacion> findByAbiertaYUsuarioInvolucrado(boolean abierta, String usuarioId)
  {
    List<ObjectId> actividadIds = actividadesMongoRepository.findByOrganizadorIdOrParticipantesId(usuarioId, usuarioId).stream()
        .map(a -> new ObjectId(a.getId()))
        .collect(Collectors.toList());
    return mongoRepository.findByAbiertaAndActividadIdIn(abierta, actividadIds).stream()
        .map(mapper::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public Votacion save(Votacion votacion)
  {
    return mapper.toDomain(mongoRepository.save(mapper.toEntity(votacion)));
  }

  @Override
  public Optional<Votacion> cerrarSiAbierta(Votacion votacion)
  {
    Query query = new Query(Criteria.where("id").is(votacion.getId()).and("abierta").is(true));
    Update update = new Update()
        .set("abierta", false)
        .set("fechaCierre", votacion.getFechaCierre())
        .set("alternativaGanadora", alternativaMapper.toEntity(votacion.getAlternativaGanadora()))
        .inc("version", 1L);

    VotacionEntity cerrada = mongoTemplate.findAndModify(
        query, update, FindAndModifyOptions.options().returnNew(true), VotacionEntity.class);
    return Optional.ofNullable(cerrada).map(mapper::toDomain);
  }

  @Override
  public Optional<Votacion> findById(String id)
  {
    return mongoRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  public void delete(Votacion votacion)
  {
    mongoRepository.delete(mapper.toEntity(votacion));
  }
}
