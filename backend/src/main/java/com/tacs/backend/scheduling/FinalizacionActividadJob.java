package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.notificacion.TipoNotificacion;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.services.NotificacionInboxService;
import com.tacs.backend.services.ServicioNotificaciones;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizacionActividadJob
{
  private final ActividadesRepository actividadesRepository;
  private final ServicioNotificaciones servicioNotificaciones;
  private final NotificacionInboxService notificacionInboxService;

  @Scheduled(fixedRate = 300000) // cada 5 min
  @SchedulerLock(name = "finalizacionActividadJob", lockAtMostFor = "4m", lockAtLeastFor = "1m")
  public void finalizarActividadesPasadas()
  {
    log.info("Iniciando chequeo de finalización de actividades pasadas...");

    LocalDateTime ahora = LocalDateTime.now();
    List<Actividad> terminadas = detectarActividadesTerminadas(ahora);

    for (Actividad entidad : terminadas)
    {
      procesarActividadTerminada(entidad);
    }

    log.info("Chequeo de finalización completado.");
  }

  public List<Actividad> detectarActividadesTerminadas(LocalDateTime ahora)
  {
    return actividadesRepository.findCandidatasParaFinalizacion(ahora).stream()
        .filter(actividad -> {
          LocalDateTime fechaFin = actividad.getFechaFin();
          if (fechaFin != null)
          {
            return !ahora.isBefore(fechaFin);
          }
          return actividad.getFechaRealizacion() != null && !ahora.isBefore(actividad.getFechaRealizacion());
        })
        .toList();
  }

  public void procesarActividadTerminada(Actividad entidad)
  {
    try
    {
      if (entidad.getEstado() == TipoEstadoActividad.PROPUESTA)
      {
        entidad.cambiarEstado(TipoEstadoActividad.CANCELADA);
        actividadesRepository.save(entidad);
        log.info("Actividad ID={} en estado PROPUESTA marcada como CANCELADA por falta de quórum", entidad.getId());

        String contenido = "La actividad '%s' fue cancelada: no alcanzó la cantidad mínima de participantes requerida."
            .formatted(entidad.getTitulo());

        if (servicioNotificaciones != null)
        {
          servicioNotificaciones.notificarATodos(contenido, entidad.getParticipantes());
        }
        if (notificacionInboxService != null)
        {
          notificacionInboxService.crearParaTodos(contenido, TipoNotificacion.ACTIVIDAD_CANCELADA,
              entidad.getId(), null, entidad.getParticipantes());
        }
      }
      else
      {
        entidad.cambiarEstado(TipoEstadoActividad.FINALIZADA);
        actividadesRepository.save(entidad);
        log.info("Actividad ID={} marcada como FINALIZADA", entidad.getId());
      }
    }
    catch (Exception e)
    {
      log.error("Error al finalizar actividad ID={}", entidad.getId(), e);
    }
  }
}
