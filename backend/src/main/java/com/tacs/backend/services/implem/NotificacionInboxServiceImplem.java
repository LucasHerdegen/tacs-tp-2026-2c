package com.tacs.backend.services.implem;

import com.tacs.backend.domain.notificacion.Notificacion;
import com.tacs.backend.domain.notificacion.TipoNotificacion;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.notificaciones.NotificacionDto;
import com.tacs.backend.exceptions.NotificacionNotFoundException;
import com.tacs.backend.repositories.NotificacionRepository;
import com.tacs.backend.services.NotificacionInboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class NotificacionInboxServiceImplem implements NotificacionInboxService
{
  private final NotificacionRepository notificacionRepository;

  @Override
  public void crear(String contenido, TipoNotificacion tipo, String actividadId, String votacionId,
                    Usuario destinatario)
  {
    Notificacion notificacion = new Notificacion();
    notificacion.setUsuarioId(destinatario.getId());
    notificacion.setContenido(contenido);
    notificacion.setTipo(tipo);
    notificacion.setActividadId(actividadId);
    notificacion.setVotacionId(votacionId);
    notificacion.setLeida(false);
    notificacion.setFechaCreacion(LocalDateTime.now());

    notificacionRepository.save(notificacion);
  }

  @Override
  public void crearParaTodos(String contenido, TipoNotificacion tipo, String actividadId, String votacionId,
                             Collection<Usuario> destinatarios)
  {
    for (Usuario destinatario : destinatarios)
    {
      try
      {
        crear(contenido, tipo, actividadId, votacionId, destinatario);
      } catch (Exception e)
      {
        log.error("Fallo persistiendo la notificacion in-app para usuario id={}", destinatario.getId(), e);
      }
    }
  }

  @Override
  public Page<NotificacionDto> obtenerNotificaciones(String usuarioId, Boolean leida, Pageable pageable)
  {
    return notificacionRepository.buscarPorUsuario(usuarioId, leida, pageable).map(this::toDto);
  }

  @Override
  public long contarNoLeidas(String usuarioId)
  {
    return notificacionRepository.contarNoLeidas(usuarioId);
  }

  @Override
  public void marcarComoLeida(String notificacionId, String usuarioId)
  {
    Notificacion notificacion = notificacionRepository.findById(notificacionId)
        .filter(n -> n.getUsuarioId().equals(usuarioId))
        .orElseThrow(() -> new NotificacionNotFoundException("Notificacion no encontrada"));

    notificacion.setLeida(true);
    notificacion.setFechaLectura(LocalDateTime.now());
    notificacionRepository.save(notificacion);
  }

  @Override
  public void marcarTodasComoLeidas(String usuarioId)
  {
    List<Notificacion> pendientes = notificacionRepository.findNoLeidasByUsuario(usuarioId);
    LocalDateTime ahora = LocalDateTime.now();
    pendientes.forEach(n -> {
      n.setLeida(true);
      n.setFechaLectura(ahora);
    });
    notificacionRepository.saveAll(pendientes);
  }

  private NotificacionDto toDto(Notificacion notificacion)
  {
    return new NotificacionDto(
        notificacion.getId(),
        notificacion.getContenido(),
        notificacion.getTipo(),
        notificacion.getActividadId(),
        notificacion.getVotacionId(),
        notificacion.isLeida(),
        notificacion.getFechaCreacion(),
        notificacion.getFechaLectura());
  }
}
