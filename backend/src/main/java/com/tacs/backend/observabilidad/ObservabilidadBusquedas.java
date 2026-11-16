package com.tacs.backend.observabilidad;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * Encapsula la instrumentación manual con OpenTelemetry API (trazas y métricas)
 * para el flujo de búsqueda de actividades disponibles.
 */
@Component
public class ObservabilidadBusquedas
{
  private static final String NOMBRE_INSTRUMENTACION = "com.tacs.backend.observabilidad.busquedas";
  private static final double NANOSEGUNDOS_POR_SEGUNDO = 1_000_000_000.0;
  private static final List<Double> LIMITES_BUCKETS_SEGUNDOS = List.of(
      0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0
  );

  private static final AttributeKey<String> ATTR_FILTRO_TIPO = AttributeKey.stringKey("actividad.filtro.tipo");
  private static final AttributeKey<String> ATTR_FILTRO_ESTADO = AttributeKey.stringKey("actividad.filtro.estado");
  private static final AttributeKey<Boolean> ATTR_FILTRO_TIENE_TEXTO =
      AttributeKey.booleanKey("actividad.filtro.tiene_busqueda_texto");
  private static final AttributeKey<Boolean> ATTR_FILTRO_TIENE_FECHAS =
      AttributeKey.booleanKey("actividad.filtro.tiene_rango_fechas");
  private static final AttributeKey<Boolean> ATTR_FILTRO_CUPO =
      AttributeKey.booleanKey("actividad.filtro.cupo_disponible");
  private static final AttributeKey<Long> ATTR_PAGINA = AttributeKey.longKey("actividad.paginacion.pagina");
  private static final AttributeKey<Long> ATTR_TAMANO_PAGINA = AttributeKey.longKey("actividad.paginacion.tamano");
  private static final AttributeKey<Long> ATTR_RESULTADOS_PAGINA =
      AttributeKey.longKey("actividad.resultados.cantidad_pagina");
  private static final AttributeKey<Long> ATTR_RESULTADOS_TOTAL = AttributeKey.longKey("actividad.resultados.total");
  private static final AttributeKey<Long> ATTR_DEMORA_SIMULADA_MS =
      AttributeKey.longKey("actividad.demora_simulada_ms");
  private static final AttributeKey<String> ATTR_ERROR_TIPO = AttributeKey.stringKey("error.type");

  private static final AttributeKey<String> ATTR_METRICA_TIPO = AttributeKey.stringKey("actividad.tipo");
  private static final AttributeKey<String> ATTR_METRICA_ESTADO = AttributeKey.stringKey("actividad.estado");
  private static final AttributeKey<Boolean> ATTR_METRICA_CON_TEXTO =
      AttributeKey.booleanKey("actividad.con_filtro_texto");
  private static final AttributeKey<Boolean> ATTR_METRICA_CON_FECHA =
      AttributeKey.booleanKey("actividad.con_filtro_fecha");
  private static final AttributeKey<Boolean> ATTR_METRICA_CON_CUPO =
      AttributeKey.booleanKey("actividad.con_cupo_disponible");

  private static final AttributeKey<String> ATTR_DB_SISTEMA = AttributeKey.stringKey("db.system");
  private static final AttributeKey<String> ATTR_DB_COLECCION = AttributeKey.stringKey("db.collection");
  private static final AttributeKey<String> ATTR_DB_OPERACION = AttributeKey.stringKey("operacion");
  private static final AttributeKey<String> ATTR_RESULTADO = AttributeKey.stringKey("resultado");

  private final Tracer tracer;
  private final LongCounter busquedasCompletadasCounter;
  private final DoubleHistogram duracionConsultaDbHistogram;

  /**
   * Inicializa los instrumentos manuales de trazas y métricas usando {@link OpenTelemetry}.
   *
   * @param openTelemetry instancia configurada por el runtime o no-op en tests aislados.
   */
  public ObservabilidadBusquedas(OpenTelemetry openTelemetry)
  {
    this.tracer = openTelemetry.getTracer(NOMBRE_INSTRUMENTACION);
    Meter meter = openTelemetry.getMeter(NOMBRE_INSTRUMENTACION);

    this.busquedasCompletadasCounter = meter
        .counterBuilder("actividad.busquedas.completadas")
        .setDescription("Cantidad de búsquedas de actividades completadas exitosamente")
        .setUnit("1")
        .build();

    this.duracionConsultaDbHistogram = meter
        .histogramBuilder("actividad.busqueda.db.duration")
        .setDescription("Duración de las consultas contra MongoDB durante la búsqueda de actividades")
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(LIMITES_BUCKETS_SEGUNDOS)
        .build();
  }

  /**
   * Envuelve la operación de negocio de búsqueda en un span manual y registra el contador
   * de búsquedas completadas únicamente cuando la operación finaliza sin errores.
   */
  public Page<ActividadDto> instrumentarBusqueda(
      TipoActividad tipo,
      String busqueda,
      LocalDate fechaInicio,
      LocalDate fechaFin,
      TipoEstadoActividad estado,
      Boolean cupoDisponible,
      Pageable pageable,
      Supplier<Page<ActividadDto>> operacion)
  {
    String tipoEtiqueta = tipo != null ? tipo.name() : "TODOS";
    String estadoEtiqueta = estado != null ? estado.name() : "ACTIVAS";
    boolean tieneTexto = busqueda != null && !busqueda.isBlank();
    boolean tieneFechas = fechaInicio != null || fechaFin != null;
    boolean requiereCupo = Boolean.TRUE.equals(cupoDisponible);

    Span span = tracer.spanBuilder("actividad.buscar")
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute(ATTR_FILTRO_TIPO, tipoEtiqueta)
        .setAttribute(ATTR_FILTRO_ESTADO, estadoEtiqueta)
        .setAttribute(ATTR_FILTRO_TIENE_TEXTO, tieneTexto)
        .setAttribute(ATTR_FILTRO_TIENE_FECHAS, tieneFechas)
        .setAttribute(ATTR_FILTRO_CUPO, requiereCupo)
        .setAttribute(ATTR_PAGINA, pageable != null ? (long) pageable.getPageNumber() : 0L)
        .setAttribute(ATTR_TAMANO_PAGINA, pageable != null ? (long) pageable.getPageSize() : 20L)
        .startSpan();

    try (Scope scope = span.makeCurrent())
    {
      Page<ActividadDto> resultado = operacion.get();
      span.setAttribute(ATTR_RESULTADOS_PAGINA, (long) resultado.getNumberOfElements());
      span.setAttribute(ATTR_RESULTADOS_TOTAL, resultado.getTotalElements());
      span.setStatus(StatusCode.OK);

      Attributes atributosMetrica = Attributes.builder()
          .put(ATTR_METRICA_TIPO, tipoEtiqueta)
          .put(ATTR_METRICA_ESTADO, estadoEtiqueta)
          .put(ATTR_METRICA_CON_TEXTO, tieneTexto)
          .put(ATTR_METRICA_CON_FECHA, tieneFechas)
          .put(ATTR_METRICA_CON_CUPO, requiereCupo)
          .build();
      busquedasCompletadasCounter.add(1L, atributosMetrica);

      return resultado;
    }
    catch (RuntimeException | Error ex)
    {
      registrarErrorEnSpan(span, ex);
      throw ex;
    }
    finally
    {
      span.end();
    }
  }

  /**
   * Mide la etapa de consulta contra MongoDB creando un span hijo y alimentando
   * el histograma de duración de acceso a datos de la búsqueda.
   */
  public <T> T medirConsultaDb(Supplier<T> consultaDb)
  {
    Span span = tracer.spanBuilder("actividad.buscar.db")
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute(ATTR_DB_SISTEMA, "mongodb")
        .setAttribute(ATTR_DB_COLECCION, "actividades")
        .setAttribute(ATTR_DB_OPERACION, "buscar_actividades")
        .startSpan();

    long inicioNanos = System.nanoTime();
    String resultado = "exito";

    try (Scope scope = span.makeCurrent())
    {
      T respuesta = consultaDb.get();
      span.setStatus(StatusCode.OK);
      return respuesta;
    }
    catch (RuntimeException | Error ex)
    {
      resultado = "error";
      registrarErrorEnSpan(span, ex);
      throw ex;
    }
    finally
    {
      double duracionSegundos = (System.nanoTime() - inicioNanos) / NANOSEGUNDOS_POR_SEGUNDO;
      Attributes atributosHistograma = Attributes.of(
          ATTR_DB_SISTEMA, "mongodb",
          ATTR_DB_COLECCION, "actividades",
          ATTR_DB_OPERACION, "buscar_actividades",
          ATTR_RESULTADO, resultado
      );
      duracionConsultaDbHistogram.record(duracionSegundos, atributosHistograma);
      span.end();
    }
  }

  /**
   * Anota en el span activo cuando se aplica una demora controlada para pruebas de carga.
   *
   * @param demoraMs duración en milisegundos configurada para simular lentitud.
   */
  public void registrarDemoraSimulada(long demoraMs)
  {
    if (demoraMs > 0)
      Span.current().setAttribute(ATTR_DEMORA_SIMULADA_MS, demoraMs);
  }

  private void registrarErrorEnSpan(Span span, Throwable ex)
  {
    String mensaje = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    span.setAttribute(ATTR_ERROR_TIPO, ex.getClass().getSimpleName());
    span.recordException(ex);
    span.setStatus(StatusCode.ERROR, mensaje);
  }
}
