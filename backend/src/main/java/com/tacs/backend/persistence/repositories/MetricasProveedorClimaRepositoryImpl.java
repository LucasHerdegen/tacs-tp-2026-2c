package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.dtos.admin.PuntoEstadoProveedorDto;
import com.tacs.backend.persistence.entities.MetricasProveedorClimaEntity;
import com.tacs.backend.persistence.mappers.MetricasProveedorClimaMapper;
import com.tacs.backend.repositories.EstadoPersistidoProveedorClima;
import com.tacs.backend.repositories.IncrementosMetricasProveedor;
import com.tacs.backend.repositories.MetricasProveedorClimaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Implementacion MongoDB de {@link MetricasProveedorClimaRepository} mediante
 * operaciones atomicas ({@code $inc}, {@code $set}, {@code $setOnInsert} y
 * {@code $push} acotado con {@code $slice}) sobre {@link MongoTemplate}.
 */
@Repository
@RequiredArgsConstructor
public class MetricasProveedorClimaRepositoryImpl implements MetricasProveedorClimaRepository
{
  private final MetricasProveedorClimaMongoRepository mongoRepository;
  private final MongoTemplate mongoTemplate;
  private final MetricasProveedorClimaMapper mapper;

  @Override
  public Optional<EstadoPersistidoProveedorClima> buscarPorProveedor(TipoProveedorClima proveedor)
  {
    if (proveedor == null)
      return Optional.empty();
    return mongoRepository.findById(proveedor.name()).map(mapper::toSnapshot);
  }

  @Override
  public void inicializarSiNoExiste(
      TipoProveedorClima proveedor,
      EstadoProveedorClima estadoInicial,
      PuntoEstadoProveedorDto puntoInicial)
  {
    Query query = Query.query(Criteria.where("_id").is(proveedor.name()));
    Update update = new Update()
        .setOnInsert("proveedor", proveedor)
        .setOnInsert("estadoActual", estadoInicial)
        .setOnInsert("estadoSondeoConfirmado", estadoInicial)
        .setOnInsert("llamadasTotales", 0L)
        .setOnInsert("llamadasExitosas", 0L)
        .setOnInsert("erroresTotales", 0L)
        .setOnInsert("respuestasInvalidas", 0L)
        .setOnInsert("timeouts", 0L)
        .setOnInsert("rechazosRateLimit", 0L)
        .setOnInsert("fallbacksActivados", 0L)
        .setOnInsert("cacheHits", 0L)
        .setOnInsert("cacheMisses", 0L)
        .setOnInsert("historialEstado", puntoInicial != null ? List.of(mapper.toEntity(puntoInicial)) : List.of());

    mongoTemplate.upsert(query, update, MetricasProveedorClimaEntity.class);
  }

  @Override
  public void incrementarContadores(TipoProveedorClima proveedor, IncrementosMetricasProveedor incrementos)
  {
    if (proveedor == null || incrementos == null || incrementos.estaVacio())
      return;

    Query query = Query.query(Criteria.where("_id").is(proveedor.name()));
    Update update = new Update()
        .setOnInsert("proveedor", proveedor)
        .setOnInsert("estadoActual", EstadoProveedorClima.OK)
        .setOnInsert("estadoSondeoConfirmado", EstadoProveedorClima.OK)
        .setOnInsert("historialEstado", List.of());

    agregarIncrementoSiDistintoDeCero(update, "llamadasTotales", incrementos.llamadasTotales());
    agregarIncrementoSiDistintoDeCero(update, "llamadasExitosas", incrementos.llamadasExitosas());
    agregarIncrementoSiDistintoDeCero(update, "erroresTotales", incrementos.erroresTotales());
    agregarIncrementoSiDistintoDeCero(update, "respuestasInvalidas", incrementos.respuestasInvalidas());
    agregarIncrementoSiDistintoDeCero(update, "timeouts", incrementos.timeouts());
    agregarIncrementoSiDistintoDeCero(update, "rechazosRateLimit", incrementos.rechazosRateLimit());
    agregarIncrementoSiDistintoDeCero(update, "fallbacksActivados", incrementos.fallbacksActivados());
    agregarIncrementoSiDistintoDeCero(update, "cacheHits", incrementos.cacheHits());
    agregarIncrementoSiDistintoDeCero(update, "cacheMisses", incrementos.cacheMisses());

    mongoTemplate.upsert(query, update, MetricasProveedorClimaEntity.class);
  }

  @Override
  public void registrarTransicionEstado(
      TipoProveedorClima proveedor,
      EstadoProveedorClima estadoActual,
      EstadoProveedorClima estadoSondeoConfirmado,
      PuntoEstadoProveedorDto punto,
      int maxHistorialPuntos)
  {
    if (proveedor == null || punto == null)
      return;

    Query query = Query.query(Criteria.where("_id").is(proveedor.name()));
    Update update = new Update()
        .setOnInsert("proveedor", proveedor)
        .set("estadoActual", estadoActual)
        .set("estadoSondeoConfirmado", estadoSondeoConfirmado);

    update.push("historialEstado")
        .slice(-Math.max(1, maxHistorialPuntos))
        .value(mapper.toEntity(punto));

    mongoTemplate.upsert(query, update, MetricasProveedorClimaEntity.class);
  }

  @Override
  public void actualizarEstadoSondeo(
      TipoProveedorClima proveedor,
      EstadoProveedorClima estadoActual,
      EstadoProveedorClima estadoSondeoConfirmado)
  {
    if (proveedor == null)
      return;

    Query query = Query.query(Criteria.where("_id").is(proveedor.name()));
    Update update = new Update()
        .setOnInsert("proveedor", proveedor)
        .set("estadoActual", estadoActual)
        .set("estadoSondeoConfirmado", estadoSondeoConfirmado);

    mongoTemplate.upsert(query, update, MetricasProveedorClimaEntity.class);
  }

  private void agregarIncrementoSiDistintoDeCero(Update update, String campo, long delta)
  {
    if (delta != 0)
      update.inc(campo, delta);
  }
}
