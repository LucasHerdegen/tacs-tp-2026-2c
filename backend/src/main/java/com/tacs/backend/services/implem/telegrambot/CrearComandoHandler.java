package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Arranca el wizard de creacion de actividad. Si el chat ya tenia una sesion
 * propia de otro flujo en curso, la pisa — /crear siempre empieza de cero.
 * Si la sesion en curso es de otra persona (en un grupo), no la pisa.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrearComandoHandler implements ComandoHandler
{
  private static final String MENSAJE_NO_IDENTIFICADO =
      "Primero necesito identificarte. Mandá /start para empezar.";
  private static final String MENSAJE_PIDE_TITULO =
      "Vamos a crear una actividad. ¿Cual es el titulo?";
  private static final String MENSAJE_FLUJO_DE_OTRA_PERSONA =
      "Hay una actividad en curso iniciada por otra persona en este chat. Esperá a que termine o se cancele.";

  private final TelegramBot telegramBot;
  private final TelegramSesionMongoRepository sesionRepository;

  @Override
  public String comando()
  {
    return "/crear";
  }

  @Override
  public void manejar(Message mensaje, String usuarioId)
  {
    long chatId = mensaje.chat().id();

    if (usuarioId == null)
    {
      telegramBot.execute(new SendMessage(chatId, MENSAJE_NO_IDENTIFICADO));
      return;
    }

    Optional<TelegramSesionEntity> sesionExistente = sesionRepository.findById(chatId);
    if (sesionExistente.isPresent() && sesionExistente.get().getFlujoActual() != null
        && !TelegramFlujoUtils.perteneceAUsuario(sesionExistente.get(), usuarioId))
    {
      telegramBot.execute(new SendMessage(chatId, MENSAJE_FLUJO_DE_OTRA_PERSONA));
      return;
    }

    TelegramSesionEntity sesion = new TelegramSesionEntity(chatId, usuarioId);
    sesion.setFlujoActual(CrearActividadFlujoHandler.FLUJO);
    sesion.setPasoActual(CrearActividadFlujoHandler.PASO_TITULO);
    sesionRepository.save(sesion);

    log.info("[Telegram] /crear: arrancando wizard para chatId={}", chatId);
    telegramBot.execute(new SendMessage(chatId, MENSAJE_PIDE_TITULO));
  }
}
