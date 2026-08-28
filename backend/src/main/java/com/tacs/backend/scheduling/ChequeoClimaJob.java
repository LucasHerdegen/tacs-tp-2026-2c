package com.tacs.backend.scheduling;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.services.ProveedorClima;
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

  @Scheduled(fixedRateString = "${clima.chequeo.intervalo-ms}")
  public void chequearClima() {
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

    for (Actividad actividad : actividadesRepository.findCandidatasParaChequeoClima()) {
      if (!dentroDeVentanaAnticipacion(actividad))
        continue;

      Clima pronostico = proveedorClima.obtenerPronostico(actividad.getUbicacion(), actividad.getFecha());

      if (!actividad.cumpleCondiciones(pronostico))
        desfavorables.add(actividad);
    }

    return desfavorables;
  }

  private boolean dentroDeVentanaAnticipacion(Actividad actividad)
  {
    LocalDateTime limiteEvaluacion = LocalDateTime.now().plusHours(actividad.getHorasAnticipacion());
    return !limiteEvaluacion.isBefore(actividad.getFecha());
  }

  private void manejarClimaDesfavorable(Actividad actividad)
  {
    // TODO: acá se engancha la notificación al organizador/participantes (US3/US8)
    // y la apertura automática de votación (US9). Por ahora solo se deja registrado
    // para poder verificar que la deteccion funciona.
    log.info("Clima desfavorable detectado para actividad id={} ('{}')",
        actividad.getId(), actividad.getTitulo());
  }
}
