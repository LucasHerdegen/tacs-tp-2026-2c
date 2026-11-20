package com.tacs.backend.scheduling;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.services.implem.clima.MonitorProveedoresClima;
import com.tacs.backend.services.implem.legacyclima.LegacyProveedorClima;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada que sondea periodicamente los endpoints de salud
 * ({@code /healthz} y {@code /readyz}) del proveedor de clima legacy y reporta
 * el resultado real a {@link MonitorProveedoresClima}.
 */
@Slf4j
@Component
@Profile("!mock-clima")
@RequiredArgsConstructor
public class ChequeoSaludClimaJob
{
  private final LegacyProveedorClima legacyProveedorClima;
  private final MonitorProveedoresClima monitorProveedoresClima;

  @Scheduled(fixedRateString = "${clima.salud.intervalo-ms:15000}")
  @SchedulerLock(name = "ChequeoSaludClimaJob_verificarSaludProveedores", lockAtLeastFor = "10s", lockAtMostFor = "1m")
  public void verificarSaludProveedores()
  {
    EstadoProveedorClima estado;
    String detalle;
    try
    {
      estado = legacyProveedorClima.verificarSalud();
      if (estado == null)
      {
        estado = EstadoProveedorClima.CAIDO;
        detalle = "Sondeo de salud sin resultado valido (/healthz y /readyz)";
      }
      else
      {
        detalle = describirEstado(estado);
      }
    } catch (Exception e)
    {
      log.warn("Error inesperado sondeando la salud del proveedor legacy: {}", e.getMessage());
      estado = EstadoProveedorClima.CAIDO;
      detalle = "Error inesperado al sondear /healthz y /readyz";
    }

    monitorProveedoresClima.registrarEstadoSalud(TipoProveedorClima.LEGACY, estado, detalle);
    monitorProveedoresClima.volcarMetricasPendientes();
  }

  private String describirEstado(EstadoProveedorClima estado)
  {
    return switch (estado)
    {
      case OK -> "Sondeo de salud OK (/healthz y /readyz operativos)";
      case DEGRADADO -> "Sondeo de salud degradado (/healthz OK, /readyz no disponible)";
      case CAIDO -> "Sondeo de salud fallido (/healthz sin respuesta o 503)";
    };
  }
}
