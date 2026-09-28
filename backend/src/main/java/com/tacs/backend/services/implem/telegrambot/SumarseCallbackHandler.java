package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.tacs.backend.services.ActividadesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Boton inline "sumarse:<id>" ofrecido por BuscarActividadesHandler. Traduce
 * CapacidadMaximaException/EstadoInvalidoException/ActividadNotFoundException
 * a un mensaje de chat en vez de dejar que la excepcion cruda llegue al usuario.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SumarseCallbackHandler implements CallbackHandler
{
  public static final String PREFIJO = "sumarse";

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
      actividadesService.unirseActividad(actividadId, usuarioId);
      return "Te sumaste a la actividad.";
    } catch (RuntimeException e)
    {
      log.warn("[Telegram] No se pudo sumar a chatId={} (data={}): {}",
          callbackQuery.maybeInaccessibleMessage().chat().id(), callbackQuery.data(), e.getMessage());
      return "No pude sumarte (" + e.getMessage() + ").";
    }
  }
}
