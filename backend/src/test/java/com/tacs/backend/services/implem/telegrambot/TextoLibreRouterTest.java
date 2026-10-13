package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextoLibreRouterTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private TelegramSesionMongoRepository sesionRepository;

  @Mock
  private TelegramIdentidadResolver identidadResolver;

  @Mock
  private Chat chat;

  @Mock
  private User remitente;

  // Deliberadamente distinto del chatId (999L): el chat es compartido en un
  // grupo, el remitente identifica siempre a la misma persona.
  private static final long REMITENTE_ID = 555L;

  private Message mensaje;

  @BeforeEach
  void setUp()
  {
    when(chat.id()).thenReturn(999L);
    when(remitente.id()).thenReturn(REMITENTE_ID);
    mensaje = mock(Message.class);
    when(mensaje.chat()).thenReturn(chat);
    when(mensaje.from()).thenReturn(remitente);
  }

  @Test
  void textoLibreSinSesionNoDisparaNadaDeNegocioYRespondeInstrucciones()
  {
    when(sesionRepository.findById(999L)).thenReturn(Optional.empty());
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    TextoLibreRouter router = new TextoLibreRouter(telegramBot, sesionRepository, identidadResolver, List.of());

    router.despachar(mensaje);

    verify(telegramBot).execute(any(SendMessage.class));
    verify(identidadResolver, never()).resolverUsuarioId(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void textoLibreConSesionSinFlujoActualNoDisparaNadaDeNegocio()
  {
    TelegramSesionEntity sesion = new TelegramSesionEntity(999L, "usr-1");
    when(sesionRepository.findById(999L)).thenReturn(Optional.of(sesion));
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    TextoLibreRouter router = new TextoLibreRouter(telegramBot, sesionRepository, identidadResolver, List.of());

    router.despachar(mensaje);

    verify(telegramBot).execute(any(SendMessage.class));
  }

  @Test
  void textoLibreConFlujoEnCursoDelegaAlHandlerCorrespondiente()
  {
    TelegramSesionEntity sesion = new TelegramSesionEntity(999L, "usr-1");
    sesion.setFlujoActual("crear_actividad");
    when(sesionRepository.findById(999L)).thenReturn(Optional.of(sesion));
    when(identidadResolver.resolverUsuarioId(REMITENTE_ID)).thenReturn(Optional.of("usr-1"));

    FlujoHandler handler = mock(FlujoHandler.class);
    when(handler.flujo()).thenReturn("crear_actividad");

    TextoLibreRouter router = new TextoLibreRouter(telegramBot, sesionRepository, identidadResolver,
        List.of(handler));

    router.despachar(mensaje);

    verify(handler).manejar(eq(mensaje), eq(sesion), eq("usr-1"));
    verify(telegramBot, never()).execute(any(SendMessage.class));
  }

  @Test
  void textoLibreDeOtraPersonaEnElMismoChatNoContinuaElFlujoAjeno()
  {
    TelegramSesionEntity sesion = new TelegramSesionEntity(999L, "usr-1");
    sesion.setFlujoActual("crear_actividad");
    when(sesionRepository.findById(999L)).thenReturn(Optional.of(sesion));
    // El remitente de este mensaje resuelve a otro usuario, no al dueño de la sesion.
    when(identidadResolver.resolverUsuarioId(REMITENTE_ID)).thenReturn(Optional.of("usr-2"));
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    FlujoHandler handler = mock(FlujoHandler.class);
    when(handler.flujo()).thenReturn("crear_actividad");

    TextoLibreRouter router = new TextoLibreRouter(telegramBot, sesionRepository, identidadResolver,
        List.of(handler));

    router.despachar(mensaje);

    verify(handler, never()).manejar(any(), any(), any());
    org.mockito.ArgumentCaptor<SendMessage> captor = org.mockito.ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getText()).contains("otra persona");
  }

  @Test
  void siElHandlerTiraUnaExcepcionNoControladaElBotIgualResponde()
  {
    TelegramSesionEntity sesion = new TelegramSesionEntity(999L, "usr-1");
    sesion.setFlujoActual("crear_actividad");
    when(sesionRepository.findById(999L)).thenReturn(Optional.of(sesion));
    when(identidadResolver.resolverUsuarioId(REMITENTE_ID)).thenReturn(Optional.of("usr-1"));

    FlujoHandler handler = mock(FlujoHandler.class);
    when(handler.flujo()).thenReturn("crear_actividad");
    org.mockito.Mockito.doThrow(new RuntimeException("boom"))
        .when(handler).manejar(any(), any(), any());
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    TextoLibreRouter router = new TextoLibreRouter(telegramBot, sesionRepository, identidadResolver,
        List.of(handler));

    router.despachar(mensaje);

    org.mockito.ArgumentCaptor<SendMessage> captor = org.mockito.ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getText()).contains("error inesperado");
  }
}
