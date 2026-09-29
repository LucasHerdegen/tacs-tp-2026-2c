package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.ActividadesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * "/buscar [texto]": busca actividades con cupo disponible, opcionalmente
 * filtrando por titulo/ciudad, y ofrece un boton "sumarse:<id>" por resultado.
 * Excluye las que el usuario ya organiza o en las que ya participa.
 */
@Component
@RequiredArgsConstructor
public class BuscarActividadesHandler implements ComandoHandler
{
  private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final int MAXIMO_RESULTADOS = 5;
  private static final String MENSAJE_NO_IDENTIFICADO =
      "Primero necesito identificarte. Mandá /start para empezar.";
  private static final String MENSAJE_SIN_RESULTADOS =
      "No encontre actividades disponibles con esa busqueda.";

  private final TelegramBot telegramBot;
  private final ActividadesService actividadesService;

  @Override
  public String comando()
  {
    return "/buscar";
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

    String busqueda = extraerBusqueda(mensaje.text());
    Page<ActividadDto> resultados = actividadesService.buscarActividades(
        null, busqueda, null, null, null, true, PageRequest.of(0, MAXIMO_RESULTADOS));

    List<ActividadDto> disponibles = resultados.stream()
        .filter(actividad -> !yaEsParteDe(actividad, usuarioId))
        .toList();

    if (disponibles.isEmpty())
    {
      telegramBot.execute(new SendMessage(chatId, MENSAJE_SIN_RESULTADOS));
      return;
    }

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    for (ActividadDto actividad : disponibles)
    {
      String texto = "➕ Sumarme — " + actividad.titulo() + " (" + actividad.fecha().format(FORMATO_FECHA) + ")";
      markup.addRow(new InlineKeyboardButton(texto)
          .callbackData(SumarseCallbackHandler.PREFIJO + ":" + actividad.id()));
    }

    telegramBot.execute(new SendMessage(chatId,
        "Actividades disponibles (toca \"Sumarme\" en la que te interese):").replyMarkup(markup));
  }

  private String extraerBusqueda(String texto)
  {
    String[] partes = texto.trim().split("\\s+", 2);
    return partes.length > 1 ? partes[1] : null;
  }

  private boolean yaEsParteDe(ActividadDto actividad, String usuarioId)
  {
    boolean esOrganizador = actividad.organizador().id().equals(usuarioId);
    boolean esParticipante = actividad.participantes().stream().map(UsuarioDto::id).anyMatch(usuarioId::equals);
    return esOrganizador || esParticipante;
  }
}
