package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;

/**
 * Maneja un boton inline presionado (callback_data), ej. "sumarse:123" o
 * "votar:45:2". Se despacha por el prefijo antes de los ":".
 */
public interface CallbackHandler
{
  /**
   * Prefijo del callback_data que maneja, sin los ":" (ej. "sumarse").
   */
  String prefijo();

  /**
   * @return texto para mostrarle al usuario como confirmacion del boton
   * (AnswerCallbackQuery) — el CallbackRouter lo envia despues de ejecutar esto.
   */
  String manejar(CallbackQuery callbackQuery, String usuarioId);
}
