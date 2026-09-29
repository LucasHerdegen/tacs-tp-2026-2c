package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.ActividadesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarActividadesHandlerTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private ActividadesService actividadesService;

  @Mock
  private Chat chat;

  private BuscarActividadesHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new BuscarActividadesHandler(telegramBot, actividadesService);
  }

  private Message mensaje(String texto)
  {
    when(chat.id()).thenReturn(999L);
    Message mensaje = mock(Message.class);
    when(mensaje.chat()).thenReturn(chat);
    org.mockito.Mockito.lenient().when(mensaje.text()).thenReturn(texto);
    return mensaje;
  }

  private ActividadDto actividad(String id, String titulo)
  {
    return actividad(id, titulo, "otro-usuario", List.of());
  }

  private ActividadDto actividad(String id, String titulo, String organizadorId, List<String> participantesIds)
  {
    UsuarioDto organizador = new UsuarioDto(organizadorId, "user-" + organizadorId, null, null);
    List<UsuarioDto> participantes = participantesIds.stream()
        .map(pid -> new UsuarioDto(pid, "user-" + pid, null, null)).toList();
    return new ActividadDto(id, titulo, null, TipoActividad.AIRE_LIBRE, null,
        LocalDateTime.of(2026, 12, 1, 18, 0), 3, 4, 10, organizador, participantes, 24, null, null, null, null);
  }

  @Test
  void elComandoQueManejaEsBuscar()
  {
    assertThat(handler.comando()).isEqualTo("/buscar");
  }

  @Test
  void sinUsuarioIdentificadoNoConsultaActividades()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    handler.manejar(mensaje("/buscar"), null);

    verify(actividadesService, never()).buscarActividades(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void sinResultadosAvisaQueNoEncontroNada()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(actividadesService.buscarActividades(isNull(), isNull(), isNull(), isNull(), isNull(), eq(true), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    handler.manejar(mensaje("/buscar"), "usr-1");

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("No encontre");
  }

  @Test
  void conTextoLibreLoUsaComoFiltroDeBusqueda()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(actividadesService.buscarActividades(isNull(), eq("futbol"), isNull(), isNull(), isNull(), eq(true), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(actividad("act-1", "Futbol 5"))));

    handler.manejar(mensaje("/buscar futbol"), "usr-1");

    verify(actividadesService).buscarActividades(isNull(), eq("futbol"), isNull(), isNull(), isNull(), eq(true), any(Pageable.class));
  }

  @Test
  void conResultadosListaUnBotonSumarsePorCadaUno()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(actividadesService.buscarActividades(isNull(), isNull(), isNull(), isNull(), isNull(), eq(true), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(actividad("act-1", "Asado"), actividad("act-2", "Truco"))));

    handler.manejar(mensaje("/buscar"), "usr-1");

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
    List<String> callbacks = java.util.Arrays.stream(markup.inlineKeyboard())
        .flatMap(java.util.Arrays::stream)
        .map(InlineKeyboardButton::callbackData)
        .toList();
    assertThat(callbacks).containsExactly("sumarse:act-1", "sumarse:act-2");
  }

  @Test
  void excluyeActividadesDondeYaEsOrganizadorOParticipante()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(actividadesService.buscarActividades(isNull(), isNull(), isNull(), isNull(), isNull(), eq(true), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(
            actividad("act-organizo", "Asado", "usr-1", List.of()),
            actividad("act-participo", "Truco", "otro-usr", List.of("usr-1")),
            actividad("act-disponible", "Futbol", "otro-usr", List.of()))));

    handler.manejar(mensaje("/buscar"), "usr-1");

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
    List<String> callbacks = java.util.Arrays.stream(markup.inlineKeyboard())
        .flatMap(java.util.Arrays::stream)
        .map(InlineKeyboardButton::callbackData)
        .toList();
    assertThat(callbacks).containsExactly("sumarse:act-disponible");
  }
}
