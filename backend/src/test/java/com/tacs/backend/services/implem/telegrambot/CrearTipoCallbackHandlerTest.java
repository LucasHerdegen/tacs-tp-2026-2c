package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class CrearTipoCallbackHandlerTest
{
  @Mock
  private TelegramSesionMongoRepository sesionRepository;

  @Mock
  private CrearActividadFlujoHandler flujoHandler;

  @Mock
  private Chat chat;

  @Mock
  private MaybeInaccessibleMessage maybeInaccessibleMessage;

  private CrearTipoCallbackHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new CrearTipoCallbackHandler(sesionRepository, flujoHandler);
  }

  private CallbackQuery callbackQuery(String data)
  {
    when(chat.id()).thenReturn(999L);
    when(maybeInaccessibleMessage.chat()).thenReturn(chat);
    CallbackQuery callbackQuery = mock(CallbackQuery.class);
    when(callbackQuery.maybeInaccessibleMessage()).thenReturn(maybeInaccessibleMessage);
    org.mockito.Mockito.lenient().when(callbackQuery.data()).thenReturn(data);
    return callbackQuery;
  }

  @Test
  void elPrefijoQueManejaEsCrearTipo()
  {
    assertThat(handler.prefijo()).isEqualTo("crear_tipo");
  }

  @Test
  void seleccionValidaDelegaAlFlujoHandler()
  {
    TelegramSesionEntity sesion = new TelegramSesionEntity(999L, "usr-1");
    sesion.setFlujoActual(CrearActividadFlujoHandler.FLUJO);
    when(sesionRepository.findById(999L)).thenReturn(Optional.of(sesion));
    when(flujoHandler.manejarSeleccionTipo(sesion, TipoActividad.AIRE_LIBRE)).thenReturn("Tipo: AIRE_LIBRE");

    String respuesta = handler.manejar(callbackQuery("crear_tipo:AIRE_LIBRE"), "usr-1");

    assertThat(respuesta).isEqualTo("Tipo: AIRE_LIBRE");
    verify(flujoHandler).manejarSeleccionTipo(sesion, TipoActividad.AIRE_LIBRE);
  }

  @Test
  void sinSesionDeCrearActividadNoDelegaNada()
  {
    when(sesionRepository.findById(999L)).thenReturn(Optional.empty());

    String respuesta = handler.manejar(callbackQuery("crear_tipo:AIRE_LIBRE"), "usr-1");

    assertThat(respuesta).isEqualTo("Esa opcion ya no es valida.");
    verify(flujoHandler, never()).manejarSeleccionTipo(any(), any());
  }
}
