package com.tacs.backend.domain.notificacion;

import com.tacs.backend.domain.usuario.MedioContacto;

public interface Notificador
{
  void enviarNotificacion(String contenido, MedioContacto destinatario);
}
