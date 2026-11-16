package com.tacs.backend.observabilidad;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ObservabilidadBusquedasTest
{
  private InMemorySpanExporter spanExporter;
  private InMemoryMetricReader metricReader;
  private OpenTelemetrySdk openTelemetrySdk;
  private ObservabilidadBusquedas observabilidad;

  @BeforeEach
  void setUp()
  {
    spanExporter = InMemorySpanExporter.create();
    metricReader = InMemoryMetricReader.create();

    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
        .build();

    SdkMeterProvider meterProvider = SdkMeterProvider.builder()
        .registerMetricReader(metricReader)
        .build();

    openTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setMeterProvider(meterProvider)
        .build();

    observabilidad = new ObservabilidadBusquedas(openTelemetrySdk);
  }

  @AfterEach
  void tearDown()
  {
    openTelemetrySdk.close();
  }

  @Test
  @DisplayName("instrumentarBusqueda exitosa crea span padre e hijo DB, sin texto libre, e incrementa contador y histograma DB (Happy Path)")
  void instrumentarBusqueda_Exitosa_RegistraSpansYMetricasAcotadas()
  {
    // Arrange
    Pageable pageable = PageRequest.of(0, 15);
    ActividadDto dto = mock(ActividadDto.class);
    Page<ActividadDto> paginaEsperada = new PageImpl<>(List.of(dto), pageable, 1);

    // Act
    Page<ActividadDto> resultado = observabilidad.instrumentarBusqueda(
        TipoActividad.AIRE_LIBRE,
        "TextoPrivadoUsuario123",
        LocalDate.of(2026, 11, 1),
        LocalDate.of(2026, 11, 10),
        TipoEstadoActividad.PROPUESTA,
        true,
        pageable,
        () -> observabilidad.medirConsultaDb(() -> paginaEsperada)
    );

    // Assert
    assertThat(resultado).isEqualTo(paginaEsperada);

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertThat(spans).hasSize(2);

    SpanData spanDb = spans.get(0);
    SpanData spanBusqueda = spans.get(1);

    assertThat(spanDb.getName()).isEqualTo("actividad.buscar.db");
    assertThat(spanDb.getParentSpanId()).isEqualTo(spanBusqueda.getSpanId());
    assertThat(spanDb.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    assertThat(spanDb.getAttributes().get(AttributeKey.stringKey("db.system"))).isEqualTo("mongodb");

    assertThat(spanBusqueda.getName()).isEqualTo("actividad.buscar");
    assertThat(spanBusqueda.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    assertThat(spanBusqueda.getAttributes().get(AttributeKey.stringKey("actividad.filtro.tipo")))
        .isEqualTo("AIRE_LIBRE");
    assertThat(spanBusqueda.getAttributes().get(AttributeKey.booleanKey("actividad.filtro.tiene_busqueda_texto")))
        .isTrue();
    assertThat(spanBusqueda.getAttributes().get(AttributeKey.longKey("actividad.resultados.total")))
        .isEqualTo(1L);
    assertThat(spanBusqueda.getAttributes().asMap().values())
        .noneMatch(valor -> String.valueOf(valor).contains("TextoPrivadoUsuario123"));

    Collection<MetricData> metricas = metricReader.collectAllMetrics();
    MetricData contadorCompletadas = metricas.stream()
        .filter(m -> m.getName().equals("actividad.busquedas.completadas"))
        .findFirst()
        .orElseThrow();
    LongPointData puntoContador = contadorCompletadas.getLongSumData().getPoints().iterator().next();
    assertThat(puntoContador.getValue()).isEqualTo(1L);
    assertThat(puntoContador.getAttributes().get(AttributeKey.stringKey("actividad.tipo")))
        .isEqualTo("AIRE_LIBRE");

    MetricData histogramaDb = metricas.stream()
        .filter(m -> m.getName().equals("actividad.busqueda.db.duration"))
        .findFirst()
        .orElseThrow();
    HistogramPointData puntoHistograma = histogramaDb.getHistogramData().getPoints().iterator().next();
    assertThat(puntoHistograma.getCount()).isEqualTo(1L);
    assertThat(puntoHistograma.getAttributes().get(AttributeKey.stringKey("resultado"))).isEqualTo("exito");
  }

  @Test
  @DisplayName("instrumentarBusqueda fallida marca span ERROR sin incrementar contador de búsquedas completadas (Failing Path)")
  void instrumentarBusqueda_ConError_MarcaErrorSinIncrementarCompletadas()
  {
    // Arrange
    Pageable pageable = PageRequest.of(0, 20);

    // Act & Assert
    assertThatThrownBy(() -> observabilidad.instrumentarBusqueda(
        null,
        null,
        LocalDate.of(2026, 12, 10),
        LocalDate.of(2026, 12, 1),
        null,
        null,
        pageable,
        () ->
        {
          throw new IllegalArgumentException("Rango inválido");
        }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Rango inválido");

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertThat(spans).hasSize(1);
    SpanData spanFallido = spans.getFirst();
    assertThat(spanFallido.getName()).isEqualTo("actividad.buscar");
    assertThat(spanFallido.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(spanFallido.getEvents()).isNotEmpty();

    Collection<MetricData> metricas = metricReader.collectAllMetrics();
    assertThat(metricas)
        .noneMatch(m -> m.getName().equals("actividad.busquedas.completadas")
            && !m.getLongSumData().getPoints().isEmpty());
  }

  @Test
  @DisplayName("medirConsultaDb fallida registra span ERROR y métrica histograma con resultado=error (Failing Path)")
  void medirConsultaDb_ConError_MarcaErrorYRegistraHistogramaError()
  {
    // Act & Assert
    assertThatThrownBy(() -> observabilidad.medirConsultaDb(() ->
    {
      throw new IllegalStateException("Timeout MongoDB");
    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Timeout MongoDB");

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertThat(spans).hasSize(1);
    SpanData spanDb = spans.getFirst();
    assertThat(spanDb.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(spanDb.getEvents()).isNotEmpty();

    MetricData histogramaDb = metricReader.collectAllMetrics().stream()
        .filter(m -> m.getName().equals("actividad.busqueda.db.duration"))
        .findFirst()
        .orElseThrow();
    HistogramPointData punto = histogramaDb.getHistogramData().getPoints().iterator().next();
    assertThat(punto.getCount()).isEqualTo(1L);
    assertThat(punto.getAttributes().get(AttributeKey.stringKey("resultado"))).isEqualTo("error");
  }
}
