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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelarComandoHandlerTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private TelegramSesionMongoRepository sesionRepository;

  @Mock
  private Chat chat;

  private CancelarComandoHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new CancelarComandoHandler(telegramBot, sesionRepository);
  }

  private Message mensaje()
  {
    when(chat.id()).thenReturn(999L);
    Message mensaje = mock(Message.class);
    when(mensaje.chat()).thenReturn(chat);
    return mensaje;
  }

  @Test
  void elComandoQueManejaEsCancelar()
  {
    assertThat(handler.comando()).isEqualTo("/cancelar");
  }

  @Test
  void conUnFlujoEnCursoLoDescartaSinEjecutarNingunaAccion()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    TelegramSesionEntity sesion = new TelegramSesionEntity(999L, "usr-1");
    sesion.setFlujoActual(CrearActividadFlujoHandler.FLUJO);
    when(sesionRepository.findById(999L)).thenReturn(Optional.of(sesion));

    handler.manejar(mensaje(), "usr-1");

    verify(sesionRepository).deleteById(999L);
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).isEqualTo("Listo, cancele lo que estabas haciendo.");
  }

  @Test
  void sinNadaEnCursoNoBorraNiRompe()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    when(sesionRepository.findById(999L)).thenReturn(Optional.empty());

    handler.manejar(mensaje(), "usr-1");

    verify(sesionRepository, never()).deleteById(any(Long.class));
  }
}
