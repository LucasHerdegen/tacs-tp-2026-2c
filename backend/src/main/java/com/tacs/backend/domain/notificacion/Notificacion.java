package com.tacs.backend.domain.notificacion;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class Notificacion
{
  private String id;

  private Long version;

  private String usuarioId;

  private String contenido;

  private TipoNotificacion tipo;

  private String actividadId;

  private String votacionId;

  private boolean leida = false;

  private LocalDateTime fechaCreacion;

  private LocalDateTime fechaLectura;
}
