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
 * "/clima": lista las actividades que el usuario organiza, con un boton por
 * cada una para arrancar el sub-wizard de ConfigurarClimaFlujoHandler. Solo
 * se listan actividades propias (ActividadesService.actividadesOrganizadas
 * ya filtra por organizador==usuarioId).
 */
@Component
@RequiredArgsConstructor
public class ClimaComandoHandler implements ComandoHandler
{
  private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final String MENSAJE_NO_IDENTIFICADO =
      "Primero necesito identificarte. Mandá /start para empezar.";
  private static final String MENSAJE_SIN_ACTIVIDADES =
      "No organizás ninguna actividad todavía. Usa /crear para armar una.";

  private final TelegramBot telegramBot;
  private final ActividadesService actividadesService;

  @Override
  public String comando()
  {
    return "/clima";
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

    List<ActividadDto> actividades = actividadesService.actividadesOrganizadas(usuarioId, null);
    if (actividades.isEmpty())
    {
      telegramBot.execute(new SendMessage(chatId, MENSAJE_SIN_ACTIVIDADES));
      return;
    }

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    for (ActividadDto actividad : actividades)
    {
      String texto = actividad.titulo() + " (" + actividad.fecha().format(FORMATO_FECHA) + ")";
      markup.addRow(new InlineKeyboardButton(texto)
          .callbackData(SeleccionarActividadClimaCallbackHandler.PREFIJO + ":" + actividad.id()));
    }

    telegramBot.execute(new SendMessage(chatId, "¿A cual actividad le querés configurar el clima?")
        .replyMarkup(markup));
  }
}
