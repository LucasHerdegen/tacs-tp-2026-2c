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
