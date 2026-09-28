package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Descarta cualquier flujo conversacional en curso (ej. el wizard de "/crear")
 * sin ejecutar ninguna accion de negocio.
 */
@Component
@RequiredArgsConstructor
public class CancelarComandoHandler implements ComandoHandler
{
  private static final String MENSAJE_CANCELADO = "Listo, cancele lo que estabas haciendo.";
  private static final String MENSAJE_NADA_QUE_CANCELAR = "No hay nada en curso para cancelar.";

  private final TelegramBot telegramBot;
  private final TelegramSesionMongoRepository sesionRepository;

  @Override
  public String comando()
  {
    return "/cancelar";
  }

  @Override
  public void manejar(Message mensaje, String usuarioId)
  {
    long chatId = mensaje.chat().id();
    Optional<TelegramSesionEntity> sesion = sesionRepository.findById(chatId);

    if (sesion.isEmpty() || sesion.get().getFlujoActual() == null)
    {
      telegramBot.execute(new SendMessage(chatId, MENSAJE_NADA_QUE_CANCELAR));
      return;
    }

    sesionRepository.deleteById(chatId);
    telegramBot.execute(new SendMessage(chatId, MENSAJE_CANCELADO));
  }
}
