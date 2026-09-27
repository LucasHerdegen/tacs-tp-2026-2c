package com.tacs.backend.repositories;

import com.tacs.backend.domain.notificacion.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface NotificacionRepository
{
  Page<Notificacion> buscarPorUsuario(String usuarioId, Boolean leida, Pageable pageable);

  long contarNoLeidas(String usuarioId);

  List<Notificacion> findNoLeidasByUsuario(String usuarioId);

  Notificacion save(Notificacion notificacion);

  List<Notificacion> saveAll(List<Notificacion> notificaciones);

  Optional<Notificacion> findById(String id);
}
