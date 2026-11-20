package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Documento MongoDB que almacena los contadores acumulados y el historial de
 * transiciones reales de salud de un proveedor de clima ({@code WEATHER_API} o
 * {@code LEGACY}), identificado por el nombre del proveedor.
 */
@Document(collection = "metricas_proveedores_clima")
@Getter
@Setter
@NoArgsConstructor
public class MetricasProveedorClimaEntity
{
  @Id
  private String id;

  private TipoProveedorClima proveedor;
  private EstadoProveedorClima estadoActual;
  private EstadoProveedorClima estadoSondeoConfirmado;
  private long llamadasTotales;
  private long llamadasExitosas;
  private long erroresTotales;
  private long respuestasInvalidas;
  private long timeouts;
  private long rechazosRateLimit;
  private long fallbacksActivados;
  private long cacheHits;
  private long cacheMisses;
  private List<PuntoEstadoProveedorEntity> historialEstado = new ArrayList<>();
}
