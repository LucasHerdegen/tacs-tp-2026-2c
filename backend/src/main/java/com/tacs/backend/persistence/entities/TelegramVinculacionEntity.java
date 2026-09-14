package com.tacs.backend.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Token de un solo uso para vincular una cuenta ya registrada por REST/JWT
 * con un chat de Telegram (deep-link {@code /start <token>}). Analogo a un
 * token de reseteo de contrasenia: vida corta, un solo uso.
 */
@Entity
@Table(name = "telegram_vinculacion")
@Getter
@Setter
@NoArgsConstructor
public class TelegramVinculacionEntity
{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String token;

  @Column(nullable = false)
  private Long usuarioId;

  @Column(nullable = false)
  private LocalDateTime expiracion;

  @Column(nullable = false)
  private boolean usado;

  public TelegramVinculacionEntity(String token, Long usuarioId, LocalDateTime expiracion)
  {
    this.token = token;
    this.usuarioId = usuarioId;
    this.expiracion = expiracion;
    this.usado = false;
  }
}
