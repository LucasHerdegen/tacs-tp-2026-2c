package com.tacs.backend.persistence.mappers;

import com.tacs.backend.dtos.admin.PuntoEstadoProveedorDto;
import com.tacs.backend.persistence.entities.MetricasProveedorClimaEntity;
import com.tacs.backend.persistence.entities.PuntoEstadoProveedorEntity;
import com.tacs.backend.repositories.EstadoPersistidoProveedorClima;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MetricasProveedorClimaMapper
{
  public EstadoPersistidoProveedorClima toSnapshot(MetricasProveedorClimaEntity entity)
  {
    List<PuntoEstadoProveedorDto> historial = entity.getHistorialEstado() == null
        ? List.of()
        : entity.getHistorialEstado().stream()
            .map(this::toDto)
            .toList();

    return new EstadoPersistidoProveedorClima(
        entity.getProveedor(),
        entity.getEstadoActual(),
        entity.getEstadoSondeoConfirmado(),
        entity.getLlamadasTotales(),
        entity.getLlamadasExitosas(),
        entity.getErroresTotales(),
        entity.getRespuestasInvalidas(),
        entity.getTimeouts(),
        entity.getRechazosRateLimit(),
        entity.getFallbacksActivados(),
        entity.getCacheHits(),
        entity.getCacheMisses(),
        historial);
  }

  public PuntoEstadoProveedorDto toDto(PuntoEstadoProveedorEntity entity)
  {
    return new PuntoEstadoProveedorDto(
        entity.getTimestamp(),
        entity.getEstado(),
        entity.getDetalle());
  }

  public PuntoEstadoProveedorEntity toEntity(PuntoEstadoProveedorDto dto)
  {
    return new PuntoEstadoProveedorEntity(
        dto.timestamp(),
        dto.estado(),
        dto.detalle());
  }
}
