package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.services.ActividadesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * "/misactividades": lista todas las actividades del usuario (organizadas y
 * participadas) con su estado actual, y ofrece un boton "bajarse:<id>" solo
 * para las que participa sin organizar (de la propia no se puede bajar).
 */
@Component
@RequiredArgsConstructor
public class MisActividadesHandler implements ComandoHandler
{
  private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final String MENSAJE_NO_IDENTIFICADO =
      "Primero necesito identificarte. Mandá /start para empezar.";
  private static final String MENSAJE_SIN_ACTIVIDADES =
      "No tenes actividades (ni organizadas ni participadas). Usa /crear o /buscar.";

  private final TelegramBot telegramBot;
  private final ActividadesService actividadesService;

  @Override
  public String comando()
  {
    return "/misactividades";
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

    List<ActividadDto> actividades = actividadesService.actividadesDelUsuario(usuarioId, null);
    if (actividades.isEmpty())
    {
      telegramBot.execute(new SendMessage(chatId, MENSAJE_SIN_ACTIVIDADES));
      return;
    }

    StringBuilder texto = new StringBuilder("Tus actividades:\n");
    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    for (ActividadDto actividad : actividades)
    {
      boolean soyOrganizador = actividad.organizador().id().equals(usuarioId);
      texto.append("\n• ").append(actividad.titulo())
          .append(" (").append(actividad.fecha().format(FORMATO_FECHA)).append(") — ")
          .append(actividad.estadoActividad())
          .append(soyOrganizador ? " (sos el organizador)" : "");

      if (!soyOrganizador)
        markup.addRow(new InlineKeyboardButton("➖ Bajarme — " + actividad.titulo())
            .callbackData(BajarseCallbackHandler.PREFIJO + ":" + actividad.id()));
    }

    SendMessage mensajeSalida = new SendMessage(chatId, texto.toString());
    if (markup.inlineKeyboard().length > 0)
      mensajeSalida.replyMarkup(markup);
    telegramBot.execute(mensajeSalida);
  }
}
