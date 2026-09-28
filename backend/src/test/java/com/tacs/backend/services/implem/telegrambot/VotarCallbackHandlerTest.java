package com.tacs.backend.services.implem.telegrambot;

import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.services.VotacionesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VotarCallbackHandlerTest
{
  @Mock
  private VotacionesService votacionesService;

  @Mock
  private Chat chat;

  @Mock
  private MaybeInaccessibleMessage maybeInaccessibleMessage;

  private VotarCallbackHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new VotarCallbackHandler(votacionesService);
  }

  private CallbackQuery callbackQuery(String votacionId, int numeroAlternativa)
  {
    org.mockito.Mockito.lenient().when(chat.id()).thenReturn(999L);
    org.mockito.Mockito.lenient().when(maybeInaccessibleMessage.chat()).thenReturn(chat);
    CallbackQuery callbackQuery = org.mockito.Mockito.mock(CallbackQuery.class);
    org.mockito.Mockito.lenient().when(callbackQuery.maybeInaccessibleMessage()).thenReturn(maybeInaccessibleMessage);
    org.mockito.Mockito.lenient().when(callbackQuery.data()).thenReturn("votar:" + votacionId + ":" + numeroAlternativa);
    return callbackQuery;
  }

  @Test
  void elPrefijoQueManejaEsVotar()
  {
    assertThat(handler.prefijo()).isEqualTo("votar");
  }

  @Test
  void seMuestraComoAlertaModal()
  {
    assertThat(handler.mostrarComoAlerta()).isTrue();
  }

  @Test
  void sinUsuarioIdentificadoNoLlamaAlService()
  {
    String respuesta = handler.manejar(callbackQuery("vot-1", 2), null);

    assertThat(respuesta).contains("/start");
  }

  @Test
  void votoValidoLlamaAlServiceConLosParametrosCorrectos()
  {
    String respuesta = handler.manejar(callbackQuery("vot-1", 2), "usr-1");

    verify(votacionesService).votar("vot-1", "usr-1", 2);
    assertThat(respuesta).isEqualTo("Tu voto quedo registrado.");
  }

  @Test
  void votoDeUnNoParticipanteTraduceLaExcepcionAMensajeDeChat()
  {
    doThrow(new NoParticipanteException("Debes ser participante de la actividad para votar"))
        .when(votacionesService).votar("vot-1", "usr-1", 2);

    String respuesta = handler.manejar(callbackQuery("vot-1", 2), "usr-1");

    assertThat(respuesta).doesNotContain("NoParticipanteException");
    assertThat(respuesta).contains("No pude registrar tu voto");
    assertThat(respuesta).contains("participante de la actividad");
  }
}
