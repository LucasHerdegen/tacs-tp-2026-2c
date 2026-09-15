package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.exceptions.TelegramVinculacionInvalidaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * "/start" sin payload -> alta nativa (US1). "/start <token>" -> vinculacion
 * de una cuenta ya existente (US2). Si el chat ya esta identificado, no hace
 * nada de negocio (evita un alta/vinculacion duplicada sobre el mismo chat).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartComandoHandler implements ComandoHandler
{
  private static final String MENSAJE_YA_VINCULADO =
      "Ya tenes una cuenta vinculada a este chat.";
  private static final String MENSAJE_BIENVENIDA_ALTA_NATIVA =
      "Listo, te di de alta con una cuenta nueva. Usa /ayuda para ver que puedo hacer.";
  private static final String MENSAJE_BIENVENIDA_VINCULACION =
      "Tu cuenta quedo vinculada a este chat. Usa /ayuda para ver que puedo hacer.";

  private final TelegramBot telegramBot;
  private final AltaNativaHandler altaNativaHandler;
  private final VinculacionHandler vinculacionHandler;

  @Override
  public String comando()
  {
    return "/start";
  }

  @Override
  public void manejar(Message mensaje, String usuarioId)
  {
    long chatId = mensaje.chat().id();

    if (usuarioId != null)
    {
      log.info("[Telegram] /start ignorado: chatId={} ya esta vinculado a usuarioId={}", chatId, usuarioId);
      telegramBot.execute(new SendMessage(chatId, MENSAJE_YA_VINCULADO));
      return;
    }

    String token = extraerToken(mensaje.text());

    if (token == null)
    {
      log.info("[Telegram] /start sin token: dando de alta un usuario nativo para chatId={}", chatId);
      altaNativaHandler.crearUsuarioNativo(chatId);
      log.info("[Telegram] Alta nativa completada para chatId={}", chatId);
      telegramBot.execute(new SendMessage(chatId, MENSAJE_BIENVENIDA_ALTA_NATIVA));
      return;
    }

    try
    {
      log.info("[Telegram] /start con token: intentando vincular chatId={}", chatId);
      vinculacionHandler.vincular(chatId, token);
      log.info("[Telegram] Vinculacion completada para chatId={}", chatId);
      telegramBot.execute(new SendMessage(chatId, MENSAJE_BIENVENIDA_VINCULACION));
    } catch (TelegramVinculacionInvalidaException e)
    {
      log.warn("[Telegram] Vinculacion rechazada para chatId={}: {}", chatId, e.getMessage());
      telegramBot.execute(new SendMessage(chatId, e.getMessage()));
    }
  }

  private String extraerToken(String texto)
  {
    String[] partes = texto.trim().split("\\s+", 2);
    return partes.length > 1 ? partes[1] : null;
  }
}
