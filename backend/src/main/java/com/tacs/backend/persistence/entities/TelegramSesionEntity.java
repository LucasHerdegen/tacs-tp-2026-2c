package com.tacs.backend.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Estado conversacional de un chat de Telegram: en que flujo/paso esta (ej.
 * creando una actividad) y que datos ya junto. Una fila por chatId. No es un
 * concepto de negocio (como Actividad/Votacion) sino infraestructura del
 * canal Telegram, por eso se accede directo desde el poller/handlers sin una
 * capa Service propia.
 */
@Entity
@Table(name = "telegram_sesion")
@Getter
@Setter
@NoArgsConstructor
public class TelegramSesionEntity
{
  @Id
  private Long chatId;

  private Long usuarioId;

  private String flujoActual;

  private String pasoActual;

  @Lob
  @Column(name = "datos_parciales")
  private String datosParciales;

  private LocalDateTime actualizadoEn;

  public TelegramSesionEntity(Long chatId, Long usuarioId)
  {
    this.chatId = chatId;
    this.usuarioId = usuarioId;
    this.datosParciales = "{}";
    this.actualizadoEn = LocalDateTime.now();
  }
}
