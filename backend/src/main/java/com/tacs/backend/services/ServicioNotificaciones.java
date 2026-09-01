package com.tacs.backend.services;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.Usuario;

import java.util.Collection;

public interface ServicioNotificaciones
{
  void notificar(String contenido, MedioContacto destinatario);

  void notificarATodos(String contenido, Collection<Usuario> destinatarios);
}
