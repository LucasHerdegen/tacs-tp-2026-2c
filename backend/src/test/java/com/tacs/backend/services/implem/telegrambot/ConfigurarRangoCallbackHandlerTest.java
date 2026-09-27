package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurarRangoCallbackHandlerTest
{
  @Mock
  private TelegramSesionMongoRepository sesionRepository;

  @Mock
  private ConfigurarClimaFlujoHandler flujoHandler;

  @Mock
  private Chat chat;

  @Mock
  private MaybeInaccessibleMessage maybeInaccessibleMessage;

  private ConfigurarRangoCallbackHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new ConfigurarRangoCallbackHandler(sesionRepository, flujoHandler);
  }

  private CallbackQuery callbackQuery(String opcion)
  {
    when(chat.id()).thenReturn(999L);
    when(maybeInaccessibleMessage.chat()).thenReturn(chat);
    CallbackQuery callbackQuery = mock(CallbackQuery.class);
    when(callbackQuery.maybeInaccessibleMessage()).thenReturn(maybeInaccessibleMessage);
    org.mockito.Mockito.lenient().when(callbackQuery.data())
        .thenReturn(ConfigurarClimaFlujoHandler.PREFIJO_CALLBACK_RANGO + ":" + opcion);
    return callbackQuery;
  }

  @Test
  void elPrefijoQueManejaEsConfigClimaRango()
  {
    assertThat(handler.prefijo()).isEqualTo("config_clima_rango");
  }

  @Test
  void sinSesionDeConfigurarClimaNoDelegaNada()
  {
    when(sesionRepository.findById(999L)).thenReturn(Optional.empty());

    String respuesta = handler.manejar(callbackQuery("SI"), "usr-1");

    assertThat(respuesta).isEqualTo("Esa opcion ya no es valida.");
  }

  @Test
  void opcionSiDelegaConPermiteReprogramacionEnTrue()
  {
    TelegramSesionEntity sesion = new TelegramSesionEntity(999L, "usr-1");
    sesion.setFlujoActual(ConfigurarClimaFlujoHandler.FLUJO);
    when(sesionRepository.findById(999L)).thenReturn(Optional.of(sesion));
    when(flujoHandler.manejarRespuestaRango(sesion, true, "usr-1")).thenReturn("Vamos a configurar la reprogramacion.");

    String respuesta = handler.manejar(callbackQuery("SI"), "usr-1");

    assertThat(respuesta).isEqualTo("Vamos a configurar la reprogramacion.");
    verify(flujoHandler).manejarRespuestaRango(sesion, true, "usr-1");
  }

  @Test
  void opcionNoDelegaConPermiteReprogramacionEnFalse()
  {
    TelegramSesionEntity sesion = new TelegramSesionEntity(999L, "usr-1");
    sesion.setFlujoActual(ConfigurarClimaFlujoHandler.FLUJO);
    when(sesionRepository.findById(999L)).thenReturn(Optional.of(sesion));
    when(flujoHandler.manejarRespuestaRango(sesion, false, "usr-1"))
        .thenReturn("Entendido, no se va a reprogramar automaticamente.");

    String respuesta = handler.manejar(callbackQuery("NO"), "usr-1");

    assertThat(respuesta).isEqualTo("Entendido, no se va a reprogramar automaticamente.");
    verify(flujoHandler).manejarRespuestaRango(sesion, false, "usr-1");
  }
}
