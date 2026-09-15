package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Despacha un mensaje que empieza con "/" al ComandoHandler registrado para
 * ese comando. Los handlers concretos se registran solos
 * como @Component — este router no conoce ninguno en particular.
 */
@Slf4j
@Component
public class ComandoRouter
{
  private static final String MENSAJE_COMANDO_NO_RECONOCIDO =
      "No reconozco ese comando. Usa /ayuda para ver la lista de comandos disponibles.";

  private final TelegramBot telegramBot;
  private final TelegramIdentidadResolver identidadResolver;
  private final Map<String, ComandoHandler> handlersPorComando;

  public ComandoRouter(TelegramBot telegramBot, TelegramIdentidadResolver identidadResolver,
      List<ComandoHandler> handlers)
  {
    this.telegramBot = telegramBot;
    this.identidadResolver = identidadResolver;
    this.handlersPorComando = handlers.stream()
        .collect(Collectors.toMap(ComandoHandler::comando, Function.identity()));
  }

  public void despachar(Message mensaje)
  {
    long chatId = mensaje.chat().id();
    String comando = extraerComando(mensaje.text());

    ComandoHandler handler = handlersPorComando.get(comando);
    if (handler == null)
    {
      log.info("[Telegram] Comando no reconocido '{}' (chatId={})", comando, chatId);
      telegramBot.execute(new SendMessage(chatId, MENSAJE_COMANDO_NO_RECONOCIDO));
      return;
    }

    String usuarioId = identidadResolver.resolverUsuarioId(chatId).orElse(null);
    log.info("[Telegram] Comando '{}' recibido (chatId={}, usuarioId={})", comando, chatId, usuarioId);
    handler.manejar(mensaje, usuarioId);
  }

  private String extraerComando(String texto)
  {
    // "/start@mi_bot hola" -> "/start" (el "@bot" solo aparece cuando el bot
    // esta en un grupo, no en chat privado, pero se soporta igual).
    String primerToken = texto.trim().split("\\s+", 2)[0];
    return primerToken.split("@", 2)[0].toLowerCase();
  }
}
