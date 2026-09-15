package com.tacs.backend.persistence.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Fila unica (id fijo = 1) que persiste el ultimo update_id de Telegram ya
 * procesado por {@code TelegramUpdatePoller}, para sobrevivir a un restart
 * del proceso sin reprocesar ni perder updates.
 */
@Document(collection = "telegram_update_offset")
@Getter
@Setter
@NoArgsConstructor
public class TelegramUpdateOffsetEntity
{
  public static final long ID_UNICO = 1L;

  @Id
  private Long id;

  private long ultimoUpdateId;

  public TelegramUpdateOffsetEntity(long ultimoUpdateId)
  {
    this.id = ID_UNICO;
    this.ultimoUpdateId = ultimoUpdateId;
  }
}
