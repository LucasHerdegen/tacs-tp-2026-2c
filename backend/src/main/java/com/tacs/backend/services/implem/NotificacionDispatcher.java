package com.tacs.backend.services.implem;

import com.tacs.backend.domain.notificacion.Notificador;
import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.exceptions.NotificadorNoDisponibleException;
import com.tacs.backend.services.ServicioNotificaciones;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
