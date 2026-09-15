package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Despacha un mensaje de texto que NO empieza con "/". Solo dispara logica de
 * negocio si el chat tiene una TelegramSesion con un flujo en curso (ej. un
 * paso pendiente del wizard de "crear actividad"); si no, es un mensaje
 * suelto sin contexto y se responde con instrucciones basicas.
 */
@Component
public class TextoLibreRouter
{
  private static final String MENSAJE_SIN_FLUJO_EN_CURSO =
      "No tengo ninguna conversacion en curso con vos. Usa /ayuda para ver que puedo hacer.";

  private final TelegramBot telegramBot;
  private final TelegramSesionMongoRepository sesionRepository;
  private final TelegramIdentidadResolver identidadResolver;
  private final Map<String, FlujoHandler> handlersPorFlujo;

  public TextoLibreRouter(TelegramBot telegramBot, TelegramSesionMongoRepository sesionRepository,
      TelegramIdentidadResolver identidadResolver, List<FlujoHandler> handlers)
  {
    this.telegramBot = telegramBot;
    this.sesionRepository = sesionRepository;
    this.identidadResolver = identidadResolver;
    this.handlersPorFlujo = handlers.stream()
        .collect(Collectors.toMap(FlujoHandler::flujo, Function.identity()));
  }

  public void despachar(Message mensaje)
  {
    long chatId = mensaje.chat().id();
    Optional<TelegramSesionEntity> sesion = sesionRepository.findById(chatId);

    if (sesion.isEmpty() || sesion.get().getFlujoActual() == null)
    {
      telegramBot.execute(new SendMessage(chatId, MENSAJE_SIN_FLUJO_EN_CURSO));
      return;
    }

    FlujoHandler handler = handlersPorFlujo.get(sesion.get().getFlujoActual());
    if (handler == null)
    {
      telegramBot.execute(new SendMessage(chatId, MENSAJE_SIN_FLUJO_EN_CURSO));
      return;
    }

    String usuarioId = identidadResolver.resolverUsuarioId(chatId).orElse(null);
    handler.manejar(mensaje, sesion.get(), usuarioId);
  }
}
