package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.tacs.backend.services.ActividadesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Boton inline "bajarse:<id>" ofrecido por MisActividadesHandler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BajarseCallbackHandler implements CallbackHandler
{
  public static final String PREFIJO = "bajarse";

  private final ActividadesService actividadesService;

  @Override
  public String prefijo()
  {
    return PREFIJO;
  }

  @Override
  public boolean mostrarComoAlerta()
  {
    return true;
  }

  @Override
  public String manejar(CallbackQuery callbackQuery, String usuarioId)
  {
    if (usuarioId == null)
      return "Primero necesitas identificarte. Mandá /start.";

    try
    {
      String actividadId = callbackQuery.data().split(":", 2)[1];
      actividadesService.bajarseActividad(actividadId, usuarioId);
      return "Te bajaste de la actividad.";
    } catch (RuntimeException e)
    {
      log.warn("[Telegram] No se pudo bajar a chatId={} (data={}): {}",
          callbackQuery.maybeInaccessibleMessage().chat().id(), callbackQuery.data(), e.getMessage());
      return "No pude bajarte (" + e.getMessage() + ").";
    }
  }
}
