package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.persistence.entities.ActividadEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActividadesMongoRepository extends MongoRepository<ActividadEntity, String>
{
  @Query("{ 'organizador': ObjectId(?0) }")
  List<ActividadEntity> findByOrganizadorId(String organizadorId);

  @Query("{ 'organizador': ObjectId(?0), 'estado': ?1 }")
  List<ActividadEntity> findByOrganizadorIdAndEstado(String organizadorId, TipoEstadoActividad estado);

  @Query("{ 'participantes': ObjectId(?0) }")
  List<ActividadEntity> findByParticipantesId(String usuarioId);

  @Query("{ 'participantes': ObjectId(?0), 'estado': ?1 }")
  List<ActividadEntity> findByParticipantesIdAndEstado(String usuarioId, TipoEstadoActividad estado);

  @Query("{ $or: [ { 'organizador': ObjectId(?0) }, { 'participantes': ObjectId(?1) } ] }")
  List<ActividadEntity> findByOrganizadorIdOrParticipantesId(String organizadorId, String participanteId);

  @Query("{ $or: [ { 'organizador': ObjectId(?0), 'estado': ?1 }, { 'participantes': ObjectId(?2), 'estado': ?3 } ] }")
  List<ActividadEntity> findByOrganizadorIdAndEstadoOrParticipantesIdAndEstado(String organizadorId, TipoEstadoActividad estado1, String participanteId, TipoEstadoActividad estado2);

  long countByEstado(TipoEstadoActividad estado);

  // findCandidatasParaChequeoClima will be implemented in the custom impl or filtered in-memory.
  // We'll just fetch by conditions here:
  @Query("{ 'estado': { $nin: ['CANCELADA', 'FINALIZADA'] }, 'reglasClima': { $exists: true, $ne: null }, 'fechaRealizacion': { $gt: ?0 } }")
  List<ActividadEntity> findActividadesActivasFuturasConClima(LocalDateTime now);

  List<ActividadEntity> findByEstadoAndRecordatorioEnviadoFalse(TipoEstadoActividad estado);

  @Query("{ 'estado': { $nin: ['CANCELADA', 'FINALIZADA'] }, 'recordatorioEnviado': false, 'fechaRealizacion': { $gt: ?0 } }")
  List<ActividadEntity> findCandidatasParaRecordatorio(LocalDateTime now);

  @Query("{ 'estado': { $nin: ['CANCELADA', 'FINALIZADA'] }, 'fechaRealizacion': { $lte: ?0 } }")
  List<ActividadEntity> findCandidatasParaFinalizacion(LocalDateTime now);
}
