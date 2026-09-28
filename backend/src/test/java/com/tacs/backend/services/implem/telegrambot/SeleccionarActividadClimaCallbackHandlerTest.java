package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.persistence.entities.TelegramSesionEntity;
import com.tacs.backend.persistence.repositories.TelegramSesionMongoRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeleccionarActividadClimaCallbackHandlerTest
{
  @Mock
  private TelegramSesionMongoRepository sesionRepository;

  @Mock
  private ActividadesService actividadesService;

  @Mock
  private ConfigurarClimaFlujoHandler flujoHandler;

  @Mock
  private Chat chat;

  @Mock
  private MaybeInaccessibleMessage maybeInaccessibleMessage;

  private SeleccionarActividadClimaCallbackHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new SeleccionarActividadClimaCallbackHandler(sesionRepository, actividadesService, flujoHandler);
  }

  private CallbackQuery callbackQuery(String actividadId)
  {
    org.mockito.Mockito.lenient().when(chat.id()).thenReturn(999L);
    org.mockito.Mockito.lenient().when(maybeInaccessibleMessage.chat()).thenReturn(chat);
    CallbackQuery callbackQuery = org.mockito.Mockito.mock(CallbackQuery.class);
    org.mockito.Mockito.lenient().when(callbackQuery.maybeInaccessibleMessage()).thenReturn(maybeInaccessibleMessage);
    org.mockito.Mockito.lenient().when(callbackQuery.data()).thenReturn("config_clima:" + actividadId);
    return callbackQuery;
  }

  private ActividadDto actividad(String id)
  {
    return new ActividadDto(id, "Asado", null, TipoActividad.AIRE_LIBRE, null,
        LocalDateTime.of(2026, 12, 1, 18, 0), 3, 4, 10, null, null, 24, null, null, null, null);
  }

  @Test
  void elPrefijoQueManejaEsConfigClima()
  {
    assertThat(handler.prefijo()).isEqualTo("config_clima");
  }

  @Test
  void sinUsuarioIdentificadoNoArrancaNada()
  {
    String respuesta = handler.manejar(callbackQuery("act-1"), null);

    assertThat(respuesta).isEqualTo("Esa opcion ya no es valida.");
    verify(sesionRepository, never()).save(any());
  }

  @Test
  void actividadAjenaNoArrancaElSubWizard()
  {
    when(actividadesService.actividadesOrganizadas("usr-1", null)).thenReturn(List.of(actividad("act-propia")));

    String respuesta = handler.manejar(callbackQuery("act-ajena"), "usr-1");

    assertThat(respuesta).isEqualTo("Esa opcion ya no es valida.");
    verify(sesionRepository, never()).save(any());
    verify(flujoHandler, never()).preguntarLluvia(any(Long.class));
  }

  @Test
  void actividadPropiaArrancaElSubWizardYPreguntaLaLluvia()
  {
    when(actividadesService.actividadesOrganizadas("usr-1", null)).thenReturn(List.of(actividad("act-1")));

    String respuesta = handler.manejar(callbackQuery("act-1"), "usr-1");

    assertThat(respuesta).isEqualTo("Vamos a configurar el clima.");
    ArgumentCaptor<TelegramSesionEntity> captor = ArgumentCaptor.forClass(TelegramSesionEntity.class);
    verify(sesionRepository).save(captor.capture());
    assertThat(captor.getValue().getFlujoActual()).isEqualTo(ConfigurarClimaFlujoHandler.FLUJO);
    assertThat(captor.getValue().getPasoActual()).isEqualTo(ConfigurarClimaFlujoHandler.PASO_LLUVIA);
    assertThat(captor.getValue().getDatosParciales()).contains("act-1");
    verify(flujoHandler).preguntarLluvia(999L);
  }
}
