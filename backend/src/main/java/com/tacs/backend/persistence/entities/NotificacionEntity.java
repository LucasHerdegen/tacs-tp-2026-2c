package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.notificacion.TipoNotificacion;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notificaciones")
@Getter
@Setter
@NoArgsConstructor
public class NotificacionEntity
{
  @Id
  private String id;

  @Version
  private Long version;

  private String usuarioId;

  private String contenido;

  private TipoNotificacion tipo;

  private String actividadId;

  private String votacionId;

  private boolean leida;

  private LocalDateTime fechaCreacion;

  private LocalDateTime fechaLectura;
}
