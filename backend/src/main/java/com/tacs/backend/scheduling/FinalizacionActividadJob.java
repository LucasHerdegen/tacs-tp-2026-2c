package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.persistence.entities.ActividadEntity;
import com.tacs.backend.persistence.repositories.ActividadesJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizacionActividadJob
{
  private final ActividadesJpaRepository actividadesRepository;

  @Scheduled(fixedRate = 300000) // cada 5 min
  @SchedulerLock(name = "finalizacionActividadJob", lockAtMostFor = "4m", lockAtLeastFor = "1m")
  @Transactional
  public void finalizarActividadesPasadas()
  {
    log.info("Iniciando chequeo de finalización de actividades pasadas...");

    List<ActividadEntity> candidatas = actividadesRepository.findCandidatasParaFinalizacion();

    for (ActividadEntity entidad : candidatas)
    {
      try
      {
        entidad.setEstado(TipoEstadoActividad.FINALIZADA);
        actividadesRepository.save(entidad);
        log.info("Actividad ID={} marcada como FINALIZADA", entidad.getId());
      } catch (Exception e)
      {
        log.error("Error al finalizar actividad ID={}", entidad.getId(), e);
      }
    }

    log.info("Chequeo de finalización completado.");
  }
}
