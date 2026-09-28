package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.dtos.votacion.AlternativaDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.services.VotacionesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * "/misvotaciones": lista las votaciones abiertas del usuario, una por
 * mensaje, con un boton "votar:<votacionId>:<numero>" por alternativa.
 */
@Component
@RequiredArgsConstructor
public class VotarHandler implements ComandoHandler
{
  private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final String MENSAJE_NO_IDENTIFICADO =
      "Primero necesito identificarte. Mandá /start para empezar.";
  private static final String MENSAJE_SIN_VOTACIONES = "No tenes votaciones abiertas.";

  private final TelegramBot telegramBot;
  private final VotacionesService votacionesService;

  @Override
  public String comando()
  {
    return "/misvotaciones";
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

    List<VotacionDto> votaciones = votacionesService.votaciones(usuarioId, true);
    if (votaciones.isEmpty())
    {
      telegramBot.execute(new SendMessage(chatId, MENSAJE_SIN_VOTACIONES));
      return;
    }

    for (VotacionDto votacion : votaciones)
      mostrarVotacion(chatId, votacion, usuarioId);
  }

  public void mostrarVotacion(long chatId, VotacionDto votacion, String usuarioId)
  {
    Optional<Integer> yaVotada = votacionesService.alternativaVotadaPor(votacion.id(), usuarioId);

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    for (AlternativaDto alternativa : votacion.alternativasDtos())
    {
      boolean esLaVotada = yaVotada.isPresent() && yaVotada.get() == alternativa.numeroAlternativa();
      String texto = (esLaVotada ? "✅ " : "") + alternativa.fecha().format(FORMATO_FECHA)
          + " (" + alternativa.cantidadVotos() + " votos)";
      markup.addRow(new InlineKeyboardButton(texto)
          .callbackData(VotarCallbackHandler.PREFIJO + ":" + votacion.id() + ":" + alternativa.numeroAlternativa()));
    }

    telegramBot.execute(new SendMessage(chatId,
        "Votacion para \"" + votacion.actividad().titulo() + "\" (elegi la fecha):").replyMarkup(markup));
  }
}
