package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Boton inline del paso "tipo" del wizard de "/crear" (callback_data
 * "crear_tipo:<TipoActividad>"). Delega el avance del wizard a
 * CrearActividadFlujoHandler para no duplicar la logica de paso/serializacion.
 */
@Component
@RequiredArgsConstructor
public class CrearTipoCallbackHandler implements CallbackHandler
{
  private final TelegramSesionMongoRepository sesionRepository;
  private final CrearActividadFlujoHandler flujoHandler;

  @Override
  public String prefijo()
  {
    return CrearActividadFlujoHandler.PREFIJO_CALLBACK_TIPO;
  }

  @Override
  public String manejar(CallbackQuery callbackQuery, String usuarioId)
  {
    long chatId = callbackQuery.maybeInaccessibleMessage().chat().id();
    Optional<TelegramSesionEntity> sesion = sesionRepository.findById(chatId);

    if (sesion.isEmpty() || !CrearActividadFlujoHandler.FLUJO.equals(sesion.get().getFlujoActual()))
      return "Esa opcion ya no es valida.";

    String tipoTexto = callbackQuery.data().split(":", 2)[1];
    TipoActividad tipo;
    
    try
    {
      tipo = TipoActividad.valueOf(tipoTexto);
    } catch (IllegalArgumentException e)
    {
      return "Opcion invalida.";
    }

    return flujoHandler.manejarSeleccionTipo(sesion.get(), tipo);
  }
}
