package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import com.tacs.backend.services.VotacionesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class ChequeoClimaJob
{
  private final ActividadesRepository actividadesRepository;
  private final ProveedorClima proveedorClima;
  private final ServicioNotificaciones servicioNotificaciones;
  private final VotacionesService votacionesService;

  /**
   * Tarea programada que verifica periodicamente el pronostico del clima
   * para las actividades proximas y abre votaciones si es desfavorable.
   */
  @Scheduled(fixedRateString = "${clima.chequeo.intervalo-ms}")
  public void chequearClima()
  {
    for (Actividad actividad : detectarClimaDesfavorable())
      manejarClimaDesfavorable(actividad);
  }

  /**
   * Recorre las actividades candidatas y devuelve las que, dentro de su ventana
   * de anticipacion, tienen un pronostico que no cumple las reglas de clima
   * configuradas. Separado de {@link #chequearClima()} para poder testear la
   * deteccion sin depender del scheduler ni de la notificacion.
   */

  List<Actividad> detectarClimaDesfavorable()
  {
    List<Actividad> desfavorables = new ArrayList<>();

    for (Actividad actividad : actividadesRepository.findCandidatasParaChequeoClima())
    {
      if (!dentroDeVentanaAnticipacion(actividad))
        continue;

      if (tieneClimaDesfavorable(actividad))
        desfavorables.add(actividad);
    }

    return desfavorables;
  }

  /**
   * Consulta el pronostico y evalua si es desfavorable. Si el proveedor de
   * clima falla, se aisla el error: esta actividad se saltea en este tick (se
   * reevalua en la proxima corrida del cron, sigue siendo candidata) en vez de
   * tirar abajo la deteccion completa para el resto de las actividades.
   */
  private boolean tieneClimaDesfavorable(Actividad actividad)
  {
    try
    {
      Clima pronostico = proveedorClima.obtenerPronostico(actividad.getUbicacion(), actividad.getFechaRealizacion());
      return !actividad.cumpleCondiciones(pronostico);
    } catch (Exception e)
    {
      log.error("Fallo consultando el pronostico para actividad id={}, se reintenta en la proxima corrida del cron",
          actividad.getId(), e);
      return false;
    }
  }

  private boolean dentroDeVentanaAnticipacion(Actividad actividad)
  {
    LocalDateTime limiteEvaluacion = LocalDateTime.now().plusHours(actividad.getHorasAnticipacion());
    return !limiteEvaluacion.isBefore(actividad.getFechaRealizacion());
  }

  private void manejarClimaDesfavorable(Actividad actividad)
  {
    log.info("Clima desfavorable detectado para actividad id={} ('{}')",
        actividad.getId(), actividad.getTitulo());

    notificarOrganizador(actividad);

    for (Usuario participante : actividad.getParticipantes())
      if (!participante.equals(actividad.getOrganizador()))
        notificarParticipante(actividad, participante);

    abrirVotacionAutomatica(actividad);
  }

  private void abrirVotacionAutomatica(Actividad actividad)
  {
    try
    {
      votacionesService.abrirVotacionAutomatica(actividad.getId());
    } catch (Exception e)
    {
      log.error("Fallo abriendo votacion automatica para actividad id={}", actividad.getId(), e);
    }
  }

  private void notificarOrganizador(Actividad actividad)
  {
    Usuario organizador = actividad.getOrganizador();
    if (organizador.getMedioContacto() == null)
    {
      log.warn(
          "Organizador id={} no tiene medio de contacto configurado, no se le notifica el clima desfavorable de la actividad id={}",
          organizador.getId(), actividad.getId());
      return;
    }

    String contenido = "Alerta de Organizador: El pronóstico para tu actividad '%s' cambió y ya no cumple las condiciones climáticas definidas. Se abrirá una votación automática para reprogramar."
        .formatted(actividad.getTitulo());

    try
    {
      servicioNotificaciones.notificar(contenido, organizador.getMedioContacto());
    } catch (Exception e)
    {
      log.error("Fallo notificando clima desfavorable al organizador id={} de actividad id={}",
          organizador.getId(), actividad.getId(), e);
    }
  }

  private void notificarParticipante(Actividad actividad, Usuario participante)
  {
    if (participante.getMedioContacto() == null)
    {
      log.warn(
          "Participante id={} no tiene medio de contacto configurado, no se le notifica el clima desfavorable de la actividad id={}",
          participante.getId(), actividad.getId());
      return;
    }

    String contenido = "El pronóstico para la actividad '%s' cambió y ya no cumple las condiciones climáticas definidas. Se abrirá una votación para reprogramar."
        .formatted(actividad.getTitulo());

    try
    {
      servicioNotificaciones.notificar(contenido, participante.getMedioContacto());
    } catch (Exception e)
    {
      log.error("Fallo notificando clima desfavorable a participante id={} de actividad id={}",
          participante.getId(), actividad.getId(), e);
    }
  }
}
