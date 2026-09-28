package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Boton Si/No del sub-wizard de "/clima" (callback_data
 * "config_clima_rango:SI" o ":NO"). Delega en ConfigurarClimaFlujoHandler
 * para no duplicar la logica de avance de paso ni la serializacion de
 * datosParciales.
 */
@Component
@RequiredArgsConstructor
public class ConfigurarRangoCallbackHandler implements CallbackHandler
{
  private final TelegramSesionMongoRepository sesionRepository;
  private final ConfigurarClimaFlujoHandler flujoHandler;

  @Override
  public String prefijo()
  {
    return ConfigurarClimaFlujoHandler.PREFIJO_CALLBACK_RANGO;
  }

  @Override
  public String manejar(CallbackQuery callbackQuery, String usuarioId)
  {
    long chatId = callbackQuery.maybeInaccessibleMessage().chat().id();
    Optional<TelegramSesionEntity> sesion = sesionRepository.findById(chatId);
    if (sesion.isEmpty() || !ConfigurarClimaFlujoHandler.FLUJO.equals(sesion.get().getFlujoActual()))
      return "Esa opcion ya no es valida.";

    boolean permiteReprogramacion = "SI".equals(callbackQuery.data().split(":", 2)[1]);
    return flujoHandler.manejarRespuestaRango(sesion.get(), permiteReprogramacion, usuarioId);
  }
}
