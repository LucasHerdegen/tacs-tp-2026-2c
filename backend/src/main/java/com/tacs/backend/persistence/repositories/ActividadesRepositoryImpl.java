package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.persistence.mappers.ActividadMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.persistence.entities.ActividadEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.bson.Document;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
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
  public Page<Actividad> buscarActividades(TipoActividad tipo, String busqueda, LocalDate fechaInicio, LocalDate fechaFin,
                                           TipoEstadoActividad estado, Boolean cupoDisponible, Pageable pageable)
  {
    Query query = new Query();

    if (tipo != null)
      query.addCriteria(Criteria.where("tipo").is(tipo));

    if (busqueda != null && !busqueda.isBlank())
    {
      String patronSinTildes = armarPatronInsensibleATildes(busqueda.trim());
      Criteria orCriteria = new Criteria().orOperator(
          Criteria.where("titulo").regex(patronSinTildes, "i"),
          Criteria.where("ubicacion.ciudad").regex(patronSinTildes, "i")
      );
      query.addCriteria(orCriteria);
    }

    if (fechaInicio != null && fechaFin != null)
    {
      LocalDateTime startOfDay = fechaInicio.atStartOfDay();
      LocalDateTime endOfDay = fechaFin.atTime(23, 59, 59, 999999999);
      query.addCriteria(Criteria.where("fechaRealizacion").gte(startOfDay).lte(endOfDay));
    }
    else if (fechaInicio != null)
    {
      LocalDateTime startOfDay = fechaInicio.atStartOfDay();
      query.addCriteria(Criteria.where("fechaRealizacion").gte(startOfDay));
    }
    else if (fechaFin != null)
    {
      LocalDateTime endOfDay = fechaFin.atTime(23, 59, 59, 999999999);
      query.addCriteria(Criteria.where("fechaRealizacion").lte(endOfDay));
    }

    if (estado != null)
      query.addCriteria(Criteria.where("estado").is(estado));
    else
      query.addCriteria(Criteria.where("estado").ne(TipoEstadoActividad.CANCELADA));

    if (Boolean.TRUE.equals(cupoDisponible))
    {
      query.addCriteria(new Criteria("$expr")
      {
        @Override
        public @NonNull Document getCriteriaObject()
        {
          return Document.parse(
              "{ \"$expr\": { \"$lt\": [ { \"$size\": { \"$ifNull\": [ \"$participantes\", [] ] } }, \"$maximoParticipantes\" ] } }");
        }
      });
    }

    long total = mongoTemplate.count(query, ActividadEntity.class);
    Pageable pageableNormalizado = normalizarPageable(pageable);
    query.with(pageableNormalizado);
    List<ActividadEntity> entities = mongoTemplate.find(query, ActividadEntity.class);
    List<Actividad> domainList = entities.stream().map(mapper::toDomain).toList();

    return PageableExecutionUtils.getPage(domainList, pageable, () -> total);
  }

  private String armarPatronInsensibleATildes(String texto)
  {
    String sinMarcas = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < sinMarcas.length(); i++)
    {
      char c = sinMarcas.charAt(i);
      switch (Character.toLowerCase(c))
      {
        case 'a' -> sb.append("[aáàäâ]");
        case 'e' -> sb.append("[eéèëê]");
        case 'i' -> sb.append("[iíìïî]");
        case 'o' -> sb.append("[oóòöô]");
        case 'u' -> sb.append("[uúùüû]");
        default -> sb.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return sb.toString();
  }

  private Pageable normalizarPageable(Pageable pageable)
  {
    if (pageable == null || pageable.isUnpaged() || pageable.getSort().isUnsorted())
      return pageable;

    List<Sort.Order> ordenes = pageable.getSort().stream()
        .map(order -> "fecha".equals(order.getProperty()) ? order.withProperty("fechaRealizacion") : order)
        .toList();

    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(ordenes));
  }

  @Override
  public Page<Actividad> buscarActividades(TipoActividad tipo, String busqueda, LocalDate fecha,
                                           TipoEstadoActividad estado, Boolean cupoDisponible, Pageable pageable)
  {
    return buscarActividades(tipo, busqueda, fecha, fecha, estado, cupoDisponible, pageable);
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
