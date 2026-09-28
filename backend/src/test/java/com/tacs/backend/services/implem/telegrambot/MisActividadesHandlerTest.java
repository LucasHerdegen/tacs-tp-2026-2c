package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.ActividadesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MisActividadesHandlerTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private ActividadesService actividadesService;

  @Mock
  private Chat chat;

  private MisActividadesHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new MisActividadesHandler(telegramBot, actividadesService);
  }

  private Message mensaje()
  {
    when(chat.id()).thenReturn(999L);
    Message mensaje = mock(Message.class);
    when(mensaje.chat()).thenReturn(chat);
    return mensaje;
  }

  private UsuarioDto usuario(String id)
  {
    return new UsuarioDto(id, "user-" + id, null, null);
  }

  private ActividadDto actividad(String id, String titulo, String organizadorId, TipoEstadoActividad estado)
  {
    return new ActividadDto(id, titulo, null, TipoActividad.AIRE_LIBRE, null,
        LocalDateTime.of(2026, 12, 1, 18, 0), 3, 4, 10, usuario(organizadorId), null, 24, null, null, estado, null);
  }

  @Test
  void elComandoQueManejaEsMisActividades()
  {
    assertThat(handler.comando()).isEqualTo("/misactividades");
  }

  @Test
  void sinUsuarioIdentificadoNoConsultaActividades()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    handler.manejar(mensaje(), null);

    verify(actividadesService, never()).actividadesDelUsuario(any(), any());
  }

  @Test
  void sinActividadesAvisaQueUseCrearOBuscar()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(actividadesService.actividadesDelUsuario("usr-1", null)).thenReturn(List.of());

    handler.manejar(mensaje(), "usr-1");

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("/crear").contains("/buscar");
  }

  @Test
  void listaTodasConSuEstadoYSoloOfreceBajarseParaLasQueNoOrganiza()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(actividadesService.actividadesDelUsuario("usr-1", null)).thenReturn(List.of(
        actividad("act-1", "Asado", "usr-1", TipoEstadoActividad.PROPUESTA),
        actividad("act-2", "Truco", "otro-usr", TipoEstadoActividad.CONFIRMADA)));

    handler.manejar(mensaje(), "usr-1");

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());

    String texto = captor.getValue().getText();
    assertThat(texto).contains("Asado").contains("PROPUESTA").contains("sos el organizador");
    assertThat(texto).contains("Truco").contains("CONFIRMADA");

    InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
    List<String> callbacks = java.util.Arrays.stream(markup.inlineKeyboard())
        .flatMap(java.util.Arrays::stream)
        .map(InlineKeyboardButton::callbackData)
        .toList();
    assertThat(callbacks).containsExactly("bajarse:act-2");
  }
}
