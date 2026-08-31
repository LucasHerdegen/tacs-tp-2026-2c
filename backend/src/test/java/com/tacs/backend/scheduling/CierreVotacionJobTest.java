package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.VotacionesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CierreVotacionJobTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);

  @Mock
  private VotacionesRepository votacionesRepository;

  @Mock
  private VotacionesService votacionesService;

  private CierreVotacionJob job;

  private void inicializarJob()
  {
    job = new CierreVotacionJob(votacionesRepository, votacionesService);
  }

  @Test
  void detectaVotacionesAbiertasConFechaLimiteVencida()
  {
    Votacion vencida = crearVotacion(10L);

    when(votacionesRepository.findByAbiertaTrueAndFechaLimiteBefore(any()))
        .thenReturn(List.of(vencida));

    inicializarJob();
    List<Votacion> resultado = job.detectarVotacionesVencidas();

    assertThat(resultado).containsExactly(vencida);
  }

  @Test
  void cierraCadaVotacionVencidaLlamandoAResolverVotacion()
  {
    Votacion vencida1 = crearVotacion(10L);
    Votacion vencida2 = crearVotacion(20L);

    when(votacionesRepository.findByAbiertaTrueAndFechaLimiteBefore(any()))
        .thenReturn(List.of(vencida1, vencida2));

    inicializarJob();
    job.cerrarVotacionesVencidas();

    verify(votacionesService).resolverVotacion(10L);
    verify(votacionesService).resolverVotacion(20L);
  }

  @Test
  void noCierraNadaSiNoHayVotacionesVencidas()
  {
    when(votacionesRepository.findByAbiertaTrueAndFechaLimiteBefore(any()))
        .thenReturn(List.of());

    inicializarJob();
    job.cerrarVotacionesVencidas();

    verify(votacionesService, never()).resolverVotacion(any());
  }

  @Test
  void unaFallaCerrandoUnaVotacionNoImpideCerrarLasDemas()
  {
    Votacion queFalla = crearVotacion(30L);
    Votacion queFunciona = crearVotacion(40L);

    when(votacionesRepository.findByAbiertaTrueAndFechaLimiteBefore(any()))
        .thenReturn(List.of(queFalla, queFunciona));
    when(votacionesService.resolverVotacion(30L))
        .thenThrow(new RuntimeException("Fallo el cierre de la votacion!"));

    inicializarJob();
    job.cerrarVotacionesVencidas(); 

    verify(votacionesService).resolverVotacion(40L);
  }

  /* Auxiliares */
  private Votacion crearVotacion(Long id)
  {
    Actividad actividad = new Actividad(
        "Asado en el parque",
        "Actividad de prueba",
        TipoActividad.AIRE_LIBRE,
        UBICACION,
        LocalDateTime.now().plusDays(1),
        LocalDateTime.now(),
        2,
        10,
        null);

    Votacion votacion = new Votacion();
    votacion.setId(id);
    votacion.setActividad(actividad);
    votacion.setAbierta(true);
    votacion.setFechaLimite(LocalDateTime.now().minusMinutes(5)); // Vencida

    return votacion;
  }
}