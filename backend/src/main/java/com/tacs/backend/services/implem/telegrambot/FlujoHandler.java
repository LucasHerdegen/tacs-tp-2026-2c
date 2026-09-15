package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.Message;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;

/**
 * Maneja el siguiente mensaje de texto libre dentro de una conversacion
 * multi-paso (ej. el wizard de "crear actividad"). Se selecciona por
 * TelegramSesionEntity.flujoActual.
 */
public interface FlujoHandler
{
  /**
   * Nombre del flujo que maneja; debe matchear TelegramSesionEntity.flujoActual.
   */
  String flujo();

  void manejar(Message mensaje, TelegramSesionEntity sesion, String usuarioId);
}
