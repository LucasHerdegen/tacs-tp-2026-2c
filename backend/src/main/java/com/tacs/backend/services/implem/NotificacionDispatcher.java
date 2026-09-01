package com.tacs.backend.services.implem;

import com.tacs.backend.domain.notificacion.Notificador;
import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.exceptions.NotificadorNoDisponibleException;
import com.tacs.backend.services.ServicioNotificaciones;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
class NotificacionDispatcher implements ServicioNotificaciones
{
  private final List<Notificador> notificadores; // Spring inyecta todos los beans Notificador

  @Override
  public void notificar(String contenido, MedioContacto destinatario)
  {
    Notificador notificador = notificadores.stream()
        .filter(n -> n.soporta(destinatario.getTipo()))
        .findFirst()
        .orElseThrow(() -> new NotificadorNoDisponibleException(
            "No hay un canal de notificacion configurado para el tipo: " + destinatario.getTipo()));

    notificador.enviarNotificacion(contenido, destinatario);
  }

  @Override
  public void notificarATodos(String contenido, Collection<Usuario> destinatarios) {
    for (Usuario destinatario : destinatarios) {
      MedioContacto medio = destinatario.getMedioContacto();

      if(medio == null) {
        log.warn("El usuario id={} no tiene medio de contacto asignado, no se le notifica",
                destinatario.getId());
        continue;
      }

      try {
        notificar(contenido, medio);
      } catch (Exception e) {
        log.error("Fallo notificando al usuario id={}", destinatario.getId(), e);
      }
    }
  }
}
