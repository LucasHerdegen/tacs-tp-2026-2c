package com.tacs.backend.scheduling;

import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.VotacionesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CierreVotacionJob
{
  private final VotacionesRepository votacionesRepository;
  private final VotacionesService votacionesService;

  /**
   * Tarea programada que busca y cierra las votaciones cuyo tiempo limite ha expirado.
   * Resuelve cada votacion para determinar la reprogramacion o cancelacion de la actividad.
   */
  @Scheduled(fixedRateString = "${votacion.cierre.intervalo-ms}")
  public void cerrarVotacionesVencidas()
  {
    for (Votacion votacion : detectarVotacionesVencidas())
      cerrarSinRomperElLoop(votacion.getId());
  }

  public List<Votacion> detectarVotacionesVencidas()
  {
    return votacionesRepository.findByAbiertaTrueAndFechaLimiteBefore(LocalDateTime.now());
  }

  private void cerrarSinRomperElLoop(Long votacionId)
  {
    try {
      votacionesService.resolverVotacion(votacionId);
      log.info("Votacion id={} cerrada automaticamente por vencimiento de fechaLimite", votacionId);
    } catch (Exception e) {
      log.error("Fallo cerrando automaticamente la votacion id={}, se reintenta en la proxima corrida del cron",
          votacionId, e);
    }
  }
}