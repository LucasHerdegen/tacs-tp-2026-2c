package com.tacs.backend.repositories;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.dtos.admin.PuntoEstadoProveedorDto;

import java.util.Optional;

/**
 * Puerto de persistencia para las metricas operativas y el historial de
 * transiciones de salud de cada proveedor de clima.
 */
public interface MetricasProveedorClimaRepository
{
  Optional<EstadoPersistidoProveedorClima> buscarPorProveedor(TipoProveedorClima proveedor);

  void inicializarSiNoExiste(
      TipoProveedorClima proveedor,
      EstadoProveedorClima estadoInicial,
      PuntoEstadoProveedorDto puntoInicial);

  void incrementarContadores(TipoProveedorClima proveedor, IncrementosMetricasProveedor incrementos);

  void registrarTransicionEstado(
      TipoProveedorClima proveedor,
      EstadoProveedorClima estadoActual,
      EstadoProveedorClima estadoSondeoConfirmado,
      PuntoEstadoProveedorDto punto,
      int maxHistorialPuntos);

  void actualizarEstadoSondeo(
      TipoProveedorClima proveedor,
      EstadoProveedorClima estadoActual,
      EstadoProveedorClima estadoSondeoConfirmado);
}
