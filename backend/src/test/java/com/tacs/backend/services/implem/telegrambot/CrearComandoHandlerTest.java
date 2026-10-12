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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrearComandoHandlerTest
{
  @Mock
  private TelegramBot telegramBot;

  @Mock
  private TelegramSesionMongoRepository sesionRepository;

  @Mock
  private Chat chat;

  private CrearComandoHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new CrearComandoHandler(telegramBot, sesionRepository);
  }

  private Message mensaje()
  {
    when(chat.id()).thenReturn(999L);
    Message mensaje = mock(Message.class);
    when(mensaje.chat()).thenReturn(chat);
    return mensaje;
  }

  @Test
  void elComandoQueManejaEsCrear()
  {
    assertThat(handler.comando()).isEqualTo("/crear");
  }

  @Test
  void sinUsuarioIdentificadoNoArrancaElWizard()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    handler.manejar(mensaje(), null);

    verify(sesionRepository, never()).save(any());
  }

  @Test
  void conUsuarioIdentificadoArrancaLaSesionEnElPasoTitulo()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));

    handler.manejar(mensaje(), "usr-1");

    ArgumentCaptor<TelegramSesionEntity> captor = ArgumentCaptor.forClass(TelegramSesionEntity.class);
    verify(sesionRepository).save(captor.capture());
    assertThat(captor.getValue().getFlujoActual()).isEqualTo(CrearActividadFlujoHandler.FLUJO);
    assertThat(captor.getValue().getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_TITULO);
    assertThat(captor.getValue().getUsuarioId()).isEqualTo("usr-1");
  }

  @Test
  void conUnFlujoDeOtraPersonaYaEnCursoEnElMismoChatNoLoPisa()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    TelegramSesionEntity sesionAjena = new TelegramSesionEntity(999L, "usr-1");
    sesionAjena.setFlujoActual(CrearActividadFlujoHandler.FLUJO);
    when(sesionRepository.findById(999L)).thenReturn(java.util.Optional.of(sesionAjena));

    // "usr-2" manda /crear mientras "usr-1" tiene un wizard en curso en el mismo chat.
    handler.manejar(mensaje(), "usr-2");

    verify(sesionRepository, never()).save(any());
    ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
    verify(telegramBot).execute(captor.capture());
    assertThat(captor.getValue().getText()).contains("otra persona");
  }

  @Test
  void conUnaSesionPropiaAbandonadaLaPisaYArrancaDeCero()
  {
    when(telegramBot.execute(any(SendMessage.class))).thenReturn(mock(SendResponse.class));
    TelegramSesionEntity sesionPropia = new TelegramSesionEntity(999L, "usr-1");
    sesionPropia.setFlujoActual(CrearActividadFlujoHandler.FLUJO);
    sesionPropia.setPasoActual(CrearActividadFlujoHandler.PASO_CUPO_MAXIMO);
    when(sesionRepository.findById(999L)).thenReturn(java.util.Optional.of(sesionPropia));

    // La propia "usr-1" manda /crear de nuevo: puede pisar su propia sesion.
    handler.manejar(mensaje(), "usr-1");

    ArgumentCaptor<TelegramSesionEntity> captor = ArgumentCaptor.forClass(TelegramSesionEntity.class);
    verify(sesionRepository).save(captor.capture());
    assertThat(captor.getValue().getPasoActual()).isEqualTo(CrearActividadFlujoHandler.PASO_TITULO);
  }
}
