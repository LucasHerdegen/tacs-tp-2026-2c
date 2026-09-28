package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.repositories.ActividadesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizacionActividadJob
{
  private final ActividadesRepository actividadesRepository;

  @Scheduled(fixedRate = 300000) // cada 5 min
  @SchedulerLock(name = "finalizacionActividadJob", lockAtMostFor = "4m", lockAtLeastFor = "1m")
  public void finalizarActividadesPasadas()
  {
    log.info("Iniciando chequeo de finalización de actividades pasadas...");

    List<Actividad> candidatas = actividadesRepository.findCandidatasParaFinalizacion(java.time.LocalDateTime.now());

    for (Actividad entidad : candidatas)
    {
      try
      {
        entidad.cambiarEstado(TipoEstadoActividad.FINALIZADA);
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
