package com.tacs.backend.domain.notificacion;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;

/**
 * Estrategia de envio para un canal de notificacion puntual (Telegram, Email, etc.).
 * Cada implementacion declara que TipoMedioContacto sabe manejar via {@link #soporta};
 * la eleccion de cual usar en cada caso la hace {@code ServicioNotificaciones}, no el
 * caller de la notificacion.
 */
public interface Notificador
{
  boolean soporta(TipoMedioContacto tipo);

  void enviarNotificacion(String contenido, MedioContacto destinatario);
}
