package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lista los comandos disponibles. Se actualiza a medida que se suman fases
 * (crear actividad, buscar, votar, etc.) — hoy solo cubre alta/vinculacion.
 */
@Component
@RequiredArgsConstructor
public class AyudaComandoHandler implements ComandoHandler
{
  private static final String MENSAJE_AYUDA = """
      Comandos disponibles:
      /start - Da de alta una cuenta nueva con este chat
      /start <token> - Vincula este chat a una cuenta ya existente
      /crear - Crea una actividad paso a paso
      /clima - Configura las reglas de clima de una actividad que organizas
      /buscar <texto opcional> - Busca actividades con cupo disponible, filtrando por titulo o ciudad
      /misactividades - Lista tus actividades (organizadas y participadas) con su estado
      /misvotaciones - Lista tus votaciones abiertas para elegir una fecha
      /cancelar - Cancela lo que estes haciendo (ej. una actividad a medio crear)
      /ayuda - Muestra este mensaje
      """;

  private final TelegramBot telegramBot;

  @Override
  public String comando()
  {
    return "/ayuda";
  }

  @Override
  public void manejar(Message mensaje, String usuarioId)
  {
    long chatId = mensaje.chat().id();
    telegramBot.execute(new SendMessage(chatId, MENSAJE_AYUDA));
  }
}
