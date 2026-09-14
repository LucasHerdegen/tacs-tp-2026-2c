package com.tacs.backend.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila unica (id fijo = 1) que persiste el ultimo update_id de Telegram ya
 * procesado por {@code TelegramUpdatePoller}, para sobrevivir a un restart
 * del proceso sin reprocesar ni perder updates (ver research.md Unknown 2).
 */
@Entity
@Table(name = "telegram_update_offset")
@Getter
@Setter
@NoArgsConstructor
public class TelegramUpdateOffsetEntity
{
  public static final long ID_UNICO = 1L;

  @Id
  private Long id;

  @Column(name = "ultimo_update_id")
  private long ultimoUpdateId;

  public TelegramUpdateOffsetEntity(long ultimoUpdateId)
  {
    this.id = ID_UNICO;
    this.ultimoUpdateId = ultimoUpdateId;
  }
}
