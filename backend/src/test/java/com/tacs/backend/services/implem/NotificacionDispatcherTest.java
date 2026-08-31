package com.tacs.backend.services.implem;

import com.tacs.backend.domain.notificacion.Notificador;
import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.exceptions.NotificadorNoDisponibleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionDispatcherTest
{
  @Mock
  private Notificador notificadorTelegram;

  @Mock
  private Notificador notificadorQueNoSoportaNada;

  @Test
  void delegaAlNotificadorQueSoportaElTipoDeMedioDeContacto()
  {
    MedioContacto destinatario = new MedioContacto("123456789", TipoMedioContacto.TELEGRAM);

    when(notificadorTelegram.soporta(TipoMedioContacto.TELEGRAM)).thenReturn(true);
    when(notificadorQueNoSoportaNada.soporta(any())).thenReturn(false);

    NotificacionDispatcher dispatcher = new NotificacionDispatcher(List.of(notificadorQueNoSoportaNada, notificadorTelegram));
    dispatcher.notificar("ALELUYA!", destinatario);

    verify(notificadorTelegram).enviarNotificacion("ALELUYA!", destinatario);
    verify(notificadorQueNoSoportaNada, never()).enviarNotificacion(any(), any());
  }

  @Test
  void lanzaExcepcionSiNingunNotificadorSoportaElTipoDeMedioDeContacto()
  {
    MedioContacto destinatario = new MedioContacto("123456789", TipoMedioContacto.TELEGRAM);

    when(notificadorQueNoSoportaNada.soporta(any())).thenReturn(false);

    NotificacionDispatcher dispatcher = new NotificacionDispatcher(List.of(notificadorQueNoSoportaNada));

    assertThatThrownBy(() -> dispatcher.notificar("ALELUYA!", destinatario))
        .isInstanceOf(NotificadorNoDisponibleException.class);

    verify(notificadorQueNoSoportaNada, never()).enviarNotificacion(any(), any());
  }
}