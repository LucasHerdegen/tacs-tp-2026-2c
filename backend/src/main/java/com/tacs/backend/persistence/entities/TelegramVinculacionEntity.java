package com.tacs.backend.persistence.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Token de un solo uso para vincular una cuenta ya registrada por REST/JWT
 * con un chat de Telegram (deep-link {@code /start <token>}). Analogo a un
 * token de reseteo de contrasenia: vida corta, un solo uso.
 */
@Document(collection = "telegram_vinculacion")
@Getter
@Setter
@NoArgsConstructor
public class TelegramVinculacionEntity
{
  @Id
  private String id;

  private String token;

  private String usuarioId;

  private LocalDateTime expiracion;

  private boolean usado;

  public TelegramVinculacionEntity(String token, String usuarioId, LocalDateTime expiracion)
  {
    this.token = token;
    this.usuarioId = usuarioId;
    this.expiracion = expiracion;
    this.usado = false;
  }
}
