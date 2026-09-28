package com.tacs.backend.dtos.notificaciones;

import com.tacs.backend.domain.notificacion.TipoNotificacion;

import java.time.LocalDateTime;

public record NotificacionDto(
    String id,
    String contenido,
    TipoNotificacion tipo,
    String actividadId,
    String votacionId,
    boolean leida,
    LocalDateTime fechaCreacion,
    LocalDateTime fechaLectura
)
{
}
