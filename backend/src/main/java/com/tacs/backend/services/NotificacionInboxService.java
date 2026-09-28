package com.tacs.backend.services;

import com.tacs.backend.domain.notificacion.TipoNotificacion;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.notificaciones.NotificacionDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

public interface NotificacionInboxService
{
  void crear(String contenido, TipoNotificacion tipo, String actividadId, String votacionId, Usuario destinatario);

  void crearParaTodos(String contenido, TipoNotificacion tipo, String actividadId, String votacionId,
                      Collection<Usuario> destinatarios);

  Page<NotificacionDto> obtenerNotificaciones(String usuarioId, Boolean leida, Pageable pageable);

  long contarNoLeidas(String usuarioId);

  void marcarComoLeida(String notificacionId, String usuarioId);

  void marcarTodasComoLeidas(String usuarioId);
}
