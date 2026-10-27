package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.admin.EstadisticasDto;
import com.tacs.backend.dtos.admin.EstadisticasProveedorClimaDto;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.services.EstadisticasService;
import com.tacs.backend.services.implem.clima.MonitorProveedoresClima;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class EstadisticasServiceImplem implements EstadisticasService
{
  private final ActividadesRepository actividadesRepository;
  private final MonitorProveedoresClima monitorProveedoresClima;

  EstadisticasServiceImplem(ActividadesRepository actividadesRepository)
  {
    this(actividadesRepository, null);
  }

  @Autowired
  EstadisticasServiceImplem(
      ActividadesRepository actividadesRepository,
      MonitorProveedoresClima monitorProveedoresClima)
  {
    this.actividadesRepository = actividadesRepository;
    this.monitorProveedoresClima = monitorProveedoresClima;
  }

  @Override
  public EstadisticasDto obtenerEstadisticas()
  {
    long creadas = actividadesRepository.count();
    long reprogramadas = actividadesRepository.countByEstado(TipoEstadoActividad.REPROGRAMADA);
    long canceladas = actividadesRepository.countByEstado(TipoEstadoActividad.CANCELADA);
    long confirmadas = actividadesRepository.countByEstado(TipoEstadoActividad.CONFIRMADA);
    long finalizadas = actividadesRepository.countByEstado(TipoEstadoActividad.FINALIZADA);

    List<EstadisticasProveedorClimaDto> proveedores = monitorProveedoresClima != null
        ? monitorProveedoresClima.obtenerEstadisticasProveedores()
        : List.of();

    return new EstadisticasDto(
        creadas,
        reprogramadas,
        canceladas,
        confirmadas,
        finalizadas,
        proveedores);
  }
}
