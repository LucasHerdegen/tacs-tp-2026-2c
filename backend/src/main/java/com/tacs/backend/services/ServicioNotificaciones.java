package com.tacs.backend.services;

import com.tacs.backend.domain.usuario.MedioContacto;

public interface ServicioNotificaciones
{
  void notificar(String contenido, MedioContacto destinatario);
}
