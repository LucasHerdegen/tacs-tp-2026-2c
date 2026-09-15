package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
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

  private Message mensaje;

  @BeforeEach
  void setUp()
  {
    when(chat.id()).thenReturn(999L);
    mensaje = mock(Message.class);
    when(mensaje.chat()).thenReturn(chat);
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
    when(identidadResolver.resolverUsuarioId(999L)).thenReturn(Optional.of("usr-1"));

    FlujoHandler handler = mock(FlujoHandler.class);
    when(handler.flujo()).thenReturn("crear_actividad");

    TextoLibreRouter router = new TextoLibreRouter(telegramBot, sesionRepository, identidadResolver,
        List.of(handler));

    router.despachar(mensaje);

    verify(handler).manejar(eq(mensaje), eq(sesion), eq("usr-1"));
    verify(telegramBot, never()).execute(any(SendMessage.class));
  }
}
