package com.tacs.backend.scheduling;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.services.implem.clima.MonitorProveedoresClima;
import com.tacs.backend.services.implem.legacyclima.LegacyProveedorClima;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChequeoSaludClimaJobTest
{
  @Mock
  private LegacyProveedorClima legacyProveedorClima;

  @Mock
  private MonitorProveedoresClima monitorProveedoresClima;

  private ChequeoSaludClimaJob job;

  @BeforeEach
  void setUp()
  {
    job = new ChequeoSaludClimaJob(legacyProveedorClima, monitorProveedoresClima);
  }

  @Test
  @DisplayName("verificarSaludProveedores reporta OK al monitor cuando /healthz y /readyz responden 200")
  void verificarSaludProveedores_legacyOk_reportaOkAlMonitor()
  {
    when(legacyProveedorClima.verificarSalud()).thenReturn(EstadoProveedorClima.OK);

    job.verificarSaludProveedores();

    verify(legacyProveedorClima).verificarSalud();
    verify(monitorProveedoresClima).registrarEstadoSalud(
        eq(TipoProveedorClima.LEGACY),
        eq(EstadoProveedorClima.OK),
        contains("/healthz y /readyz operativos"));
    verify(monitorProveedoresClima).volcarMetricasPendientes();
  }

  @Test
  @DisplayName("verificarSaludProveedores reporta DEGRADADO al monitor cuando /readyz no esta listo")
  void verificarSaludProveedores_legacyDegradado_reportaDegradadoAlMonitor()
  {
    when(legacyProveedorClima.verificarSalud()).thenReturn(EstadoProveedorClima.DEGRADADO);

    job.verificarSaludProveedores();

    verify(legacyProveedorClima).verificarSalud();
    verify(monitorProveedoresClima).registrarEstadoSalud(
        eq(TipoProveedorClima.LEGACY),
        eq(EstadoProveedorClima.DEGRADADO),
        contains("/readyz no disponible"));
  }

  @Test
  @DisplayName("verificarSaludProveedores reporta CAIDO al monitor cuando /healthz falla")
  void verificarSaludProveedores_legacyCaido_reportaCaidoAlMonitor()
  {
    when(legacyProveedorClima.verificarSalud()).thenReturn(EstadoProveedorClima.CAIDO);

    job.verificarSaludProveedores();

    verify(legacyProveedorClima).verificarSalud();
    verify(monitorProveedoresClima).registrarEstadoSalud(
        eq(TipoProveedorClima.LEGACY),
        eq(EstadoProveedorClima.CAIDO),
        contains("/healthz sin respuesta o 503"));
  }

  @Test
  @DisplayName("verificarSaludProveedores aisla excepciones inesperadas y reporta CAIDO sin romper el scheduler")
  void verificarSaludProveedores_excepcionInesperadaAlSondear_reportaCaidoSinPropagarExcepcion()
  {
    when(legacyProveedorClima.verificarSalud()).thenThrow(new IllegalStateException("Fallo de red inesperado"));

    job.verificarSaludProveedores();

    verify(monitorProveedoresClima).registrarEstadoSalud(
        eq(TipoProveedorClima.LEGACY),
        eq(EstadoProveedorClima.CAIDO),
        contains("Error inesperado"));
  }
}
