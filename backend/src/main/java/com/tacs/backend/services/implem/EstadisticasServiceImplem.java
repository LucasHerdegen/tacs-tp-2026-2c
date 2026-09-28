package com.tacs.backend.services.implem;


import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.admin.EstadisticasDto;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.services.EstadisticasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
class EstadisticasServiceImplem implements EstadisticasService
{
  private final ActividadesRepository actividadesRepository;
  @Override
  public EstadisticasDto obtenerEstadisticas()
  {
    long creadas = actividadesRepository.count();
    long reprogramadas = actividadesRepository.countByEstado(TipoEstadoActividad.REPROGRAMADA);
    long canceladas = actividadesRepository.countByEstado(TipoEstadoActividad.CANCELADA);
    long confirmadas = actividadesRepository.countByEstado(TipoEstadoActividad.CONFIRMADA);
    long finalizadas = actividadesRepository.countByEstado(TipoEstadoActividad.FINALIZADA);

    return new EstadisticasDto(creadas, reprogramadas, canceladas, confirmadas, finalizadas);
  }
}
