package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadResumenDto;
import com.tacs.backend.dtos.votacion.AlternativaDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.services.VotacionesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VotarHandlerTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private VotacionesService votacionesService;

  @Mock
  private Chat chat;

  private VotarHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new VotarHandler(telegramBot, votacionesService);
  }

  private Message mensaje()
  {
    when(chat.id()).thenReturn(999L);
    Message mensaje = mock(Message.class);
    when(mensaje.chat()).thenReturn(chat);
    return mensaje;
  }

  private AlternativaDto alternativa(int numero, long votos)
  {
    return new AlternativaDto("alt-" + numero, LocalDateTime.of(2026, 12, 1, 18, 0), null, numero, votos, null);
  }

  private VotacionDto votacion(String id, AlternativaDto... alternativas)
  {
    ActividadResumenDto actividad = new ActividadResumenDto("act-1", "Asado", TipoEstadoActividad.PROPUESTA,
        LocalDateTime.of(2026, 12, 1, 18, 0));
    return new VotacionDto(id, LocalDateTime.now(), LocalDateTime.now().plusDays(1), null, actividad,
        List.of(alternativas), 2, true, null);
  }

  @Test
  void elComandoQueManejaEsMisVotaciones()
  {
    assertThat(handler.comando()).isEqualTo("/misvotaciones");
  }

  @Test
  void sinUsuarioIdentificadoNoConsultaVotaciones()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    handler.manejar(mensaje(), null);

    verify(votacionesService, never()).votaciones(any(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void sinVotacionesAbiertasAvisaQueNoHayNada()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(votacionesService.votaciones("usr-1", true)).thenReturn(List.of());

    handler.manejar(mensaje(), "usr-1");

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("No tenes votaciones abiertas");
  }

  @Test
  void conVotacionAbiertaListaUnBotonPorAlternativa()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(votacionesService.votaciones("usr-1", true)).thenReturn(List.of(votacion("vot-1",
        alternativa(1, 0), alternativa(2, 1))));
    when(votacionesService.alternativaVotadaPor("vot-1", "usr-1")).thenReturn(Optional.empty());

    handler.manejar(mensaje(), "usr-1");

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
    List<String> callbacks = java.util.Arrays.stream(markup.inlineKeyboard())
        .flatMap(java.util.Arrays::stream)
        .map(InlineKeyboardButton::callbackData)
        .toList();
    assertThat(callbacks).containsExactly("votar:vot-1:1", "votar:vot-1:2");
  }

  @Test
  void marcaConUnCheckLaAlternativaQueElUsuarioYaVoto()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(votacionesService.votaciones("usr-1", true)).thenReturn(List.of(votacion("vot-1",
        alternativa(1, 0), alternativa(2, 1))));
    when(votacionesService.alternativaVotadaPor("vot-1", "usr-1")).thenReturn(Optional.of(2));

    handler.manejar(mensaje(), "usr-1");

    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
    List<String> textos = java.util.Arrays.stream(markup.inlineKeyboard())
        .flatMap(java.util.Arrays::stream)
        .map(InlineKeyboardButton::text)
        .toList();
    assertThat(textos.get(0)).doesNotContain("✅");
    assertThat(textos.get(1)).contains("✅");
  }
}
