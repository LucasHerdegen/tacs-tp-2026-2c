package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.Message;

/**
 * Maneja un comando de Telegram (ej. "/start", "/crear"). Los flujos concretos implementan 
 * esta interfaz como @Component y quedan registrados automaticamente en el ComandoRouter.
 */
public interface ComandoHandler
{
  /**
   * El comando que maneja, con la barra incluida y en minusculas (ej. "/start").
   */
  String comando();

  /**
   * @param usuarioId id del Usuario ya vinculado a este chat, o null si el chat
   *                  todavia no esta identificado (ej. primer contacto con el bot).
   */
  void manejar(Message mensaje, String usuarioId);
}
