package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Despacha un boton inline presionado (CallbackQuery) al CallbackHandler
 * registrado para el prefijo de su callback_data (ej. "sumarse:123" -> "sumarse").
 */
@Component
public class CallbackRouter
{
  private static final String MENSAJE_ACCION_NO_RECONOCIDA = "No pude procesar esa accion.";

  private final TelegramBot telegramBot;
  private final TelegramIdentidadResolver identidadResolver;
  private final Map<String, CallbackHandler> handlersPorPrefijo;

  public CallbackRouter(TelegramBot telegramBot, TelegramIdentidadResolver identidadResolver,
      List<CallbackHandler> handlers)
  {
    this.telegramBot = telegramBot;
    this.identidadResolver = identidadResolver;
    this.handlersPorPrefijo = handlers.stream()
        .collect(Collectors.toMap(CallbackHandler::prefijo, Function.identity()));
  }

  public void despachar(CallbackQuery callbackQuery)
  {
    String prefijo = extraerPrefijo(callbackQuery.data());
    CallbackHandler handler = handlersPorPrefijo.get(prefijo);

    String respuesta;
    if (handler == null)
    {
      respuesta = MENSAJE_ACCION_NO_RECONOCIDA;
    }
    else
    {
      long chatId = callbackQuery.maybeInaccessibleMessage().chat().id();
      String usuarioId = identidadResolver.resolverUsuarioId(chatId).orElse(null);
      respuesta = handler.manejar(callbackQuery, usuarioId);
    }

    telegramBot.execute(new AnswerCallbackQuery(callbackQuery.id()).text(respuesta));
  }

  private String extraerPrefijo(String callbackData)
  {
    if (callbackData == null)
      return "";
    return callbackData.split(":", 2)[0];
  }
}
