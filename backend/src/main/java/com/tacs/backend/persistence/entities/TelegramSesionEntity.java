package com.tacs.backend.persistence.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Estado conversacional de un chat de Telegram: en que flujo/paso esta (ej.
 * creando una actividad) y que datos ya junto. Una fila por chatId. No es un
 * concepto de negocio (como Actividad/Votacion) sino infraestructura del
 * canal Telegram, por eso se accede directo desde el poller/handlers sin una
 * capa Service propia.
 */
@Document(collection = "telegram_sesion")
@Getter
@Setter
@NoArgsConstructor
public class TelegramSesionEntity
{
  @Id
  private Long chatId;

  private String usuarioId;

  private String flujoActual;

  private String pasoActual;

  private String datosParciales;

  private LocalDateTime actualizadoEn;

  public TelegramSesionEntity(Long chatId, String usuarioId)
  {
    this.chatId = chatId;
    this.usuarioId = usuarioId;
    this.datosParciales = "{}";
    this.actualizadoEn = LocalDateTime.now();
  }
}
