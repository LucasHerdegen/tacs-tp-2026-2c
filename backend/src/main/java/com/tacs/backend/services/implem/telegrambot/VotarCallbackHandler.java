package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.tacs.backend.services.VotacionesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Boton inline "votar:<votacionId>:<numeroAlternativa>" ofrecido por
 * VotarHandler. Traduce NoParticipanteException/VotacionCerradaException a
 * un mensaje de chat en vez de dejar que la excepcion cruda llegue al usuario.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VotarCallbackHandler implements CallbackHandler
{
  public static final String PREFIJO = "votar";

  private final VotacionesService votacionesService;

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
      String[] partes = callbackQuery.data().split(":", 3);
      String votacionId = partes[1];
      int numeroAlternativa = Integer.parseInt(partes[2]);

      votacionesService.votar(votacionId, usuarioId, numeroAlternativa);
      return "Tu voto quedo registrado.";
    } catch (RuntimeException e)
    {
      log.warn("[Telegram] No se pudo registrar el voto de chatId={} (data={}): {}",
          callbackQuery.maybeInaccessibleMessage().chat().id(), callbackQuery.data(), e.getMessage());
      return "No pude registrar tu voto (" + e.getMessage() + ").";
    }
  }
}
