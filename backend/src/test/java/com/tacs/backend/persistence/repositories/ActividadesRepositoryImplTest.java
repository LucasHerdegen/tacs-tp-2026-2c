package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.persistence.entities.ActividadEntity;
import com.tacs.backend.persistence.mappers.ActividadMapper;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActividadesRepositoryImplTest
{
  @Mock
  private ActividadesMongoRepository mongoRepository;

  @Mock
  private VotacionesMongoRepository votacionesMongoRepository;

  @Mock
  private ActividadMapper mapper;

  @Mock
  private MongoTemplate mongoTemplate;

  @InjectMocks
  private ActividadesRepositoryImpl repository;

  @Test
  @DisplayName("buscarActividades con filtros completos incluye criterio $expr para cupo disponible y rango de fechas (Happy Path)")
  void buscarActividades_ConFiltrosCompletos_ArmaQueryCorrectaYRetornaPagina()
  {
    // Arrange
    TipoActividad tipo = TipoActividad.AIRE_LIBRE;
    String busqueda = "parque";
    LocalDate fechaInicio = LocalDate.of(2026, 11, 1);
    LocalDate fechaFin = LocalDate.of(2026, 11, 5);
    TipoEstadoActividad estado = TipoEstadoActividad.PROPUESTA;
    Boolean cupoDisponible = true;
    Pageable pageable = PageRequest.of(0, 10);

    ActividadEntity entityMock = mock(ActividadEntity.class);
    Actividad domainMock = mock(Actividad.class);

    when(mongoTemplate.count(any(Query.class), eq(ActividadEntity.class))).thenReturn(1L);
    when(mongoTemplate.find(any(Query.class), eq(ActividadEntity.class))).thenReturn(List.of(entityMock));
    when(mapper.toDomain(entityMock)).thenReturn(domainMock);

    // Act
    Page<Actividad> result = repository.buscarActividades(
        tipo, busqueda, fechaInicio, fechaFin, estado, cupoDisponible, pageable);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isEqualTo(1L);
    assertThat(result.getContent()).containsExactly(domainMock);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).count(queryCaptor.capture(), eq(ActividadEntity.class));

    Query queryEnviada = queryCaptor.getValue();
    Document criteriaDoc = queryEnviada.getQueryObject();

    // Verificamos que contenga los filtros
    assertThat(criteriaDoc.get("tipo")).isEqualTo(TipoActividad.AIRE_LIBRE);
    assertThat(criteriaDoc.containsKey("$or")).isTrue();
    assertThat(criteriaDoc.containsKey("fechaRealizacion")).isTrue();
    assertThat(criteriaDoc.get("estado")).isEqualTo(TipoEstadoActividad.PROPUESTA);

    // Verificamos que contenga el criterio con los operadores $ reparados
    assertThat(criteriaDoc.containsKey("$expr")).isTrue();
    Document exprDoc = (Document) criteriaDoc.get("$expr");
    assertThat(exprDoc).isNotNull();
    assertThat(exprDoc.containsKey("$lt")).isTrue();
    List<?> ltArgs = (List<?>) exprDoc.get("$lt");
    assertThat(ltArgs).hasSize(2);

    Document sizeDoc = (Document) ltArgs.get(0);
    assertThat(sizeDoc.containsKey("$size")).isTrue();
    Document ifNullDoc = (Document) sizeDoc.get("$size");
    assertThat(ifNullDoc.containsKey("$ifNull")).isTrue();
    List<?> ifNullArgs = (List<?>) ifNullDoc.get("$ifNull");
    assertThat(ifNullArgs.get(0)).isEqualTo("$participantes");
    assertThat(ifNullArgs.get(1)).isEqualTo(List.of());

    assertThat(ltArgs.get(1)).isEqualTo("$maximoParticipantes");
  }

  @Test
  @DisplayName("buscarActividades sin filtro de estado excluye las CANCELADAS por defecto")
  void buscarActividades_SinFiltros_ExcluyeCanceladasPorDefecto()
  {
    // Arrange
    Pageable pageable = PageRequest.of(0, 5);

    when(mongoTemplate.count(any(Query.class), eq(ActividadEntity.class))).thenReturn(0L);
    when(mongoTemplate.find(any(Query.class), eq(ActividadEntity.class))).thenReturn(List.of());

    // Act
    Page<Actividad> result = repository.buscarActividades(null, null, null, null, null, false, pageable);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.getContent()).isEmpty();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).count(queryCaptor.capture(), eq(ActividadEntity.class));

    Query queryEnviada = queryCaptor.getValue();
    Document estadoDoc = (Document) queryEnviada.getQueryObject().get("estado");
    assertThat(estadoDoc).isNotNull();
    assertThat(estadoDoc.get("$ne")).isEqualTo(TipoEstadoActividad.CANCELADA);
  }

  @Test
  @DisplayName("buscarActividades ignora tildes en el texto de búsqueda ('Cordoba' matchea 'Córdoba')")
  void buscarActividades_BusquedaIgnoraTildes_GeneraRegexConClasesDeVocales()
  {
    // Arrange
    Pageable pageable = PageRequest.of(0, 5);
    when(mongoTemplate.count(any(Query.class), eq(ActividadEntity.class))).thenReturn(0L);
    when(mongoTemplate.find(any(Query.class), eq(ActividadEntity.class))).thenReturn(List.of());

    // Act
    repository.buscarActividades(null, "Cordoba", null, null, null, false, pageable);

    // Assert
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(ActividadEntity.class));

    Document queryDoc = queryCaptor.getValue().getQueryObject();
    @SuppressWarnings("unchecked")
    List<Document> orClauses = (List<Document>) queryDoc.get("$or");
    String regexGenerada = orClauses.getFirst().get("titulo").toString();

    assertThat("Córdoba".matches("(?i).*" + orClauses.getFirst().get("titulo", Pattern.class).pattern() + ".*")).isTrue();
    assertThat("Cordoba".matches("(?i).*" + orClauses.getFirst().get("titulo", Pattern.class).pattern() + ".*")).isTrue();
    assertThat(regexGenerada).contains("[oóòöô]");
  }

  @Test
  @DisplayName("buscarActividades traduce sort=fecha al campo persistido fechaRealizacion")
  void buscarActividades_SortPorFecha_TraduceAFechaRealizacion()
  {
    // Arrange
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "fecha"));
    when(mongoTemplate.count(any(Query.class), eq(ActividadEntity.class))).thenReturn(0L);
    when(mongoTemplate.find(any(Query.class), eq(ActividadEntity.class))).thenReturn(List.of());

    // Act
    repository.buscarActividades(null, null, null, null, null, false, pageable);

    // Assert
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(ActividadEntity.class));

    Document sortDoc = queryCaptor.getValue().getSortObject();
    assertThat(sortDoc.getInteger("fechaRealizacion")).isEqualTo(1);
    assertThat(sortDoc.containsKey("fecha")).isFalse();
  }
}

