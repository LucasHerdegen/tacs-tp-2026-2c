package com.tacs.backend.services.implem.telegrambot;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.dtos.auth.RegistroRequest;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AltaNativaHandlerTest
{
  @Mock
  private AuthService authService;

  @Test
  void creaUnUsuarioConUsernameDerivadoDelRemitenteIdYLoVinculaComoMedioDeContacto()
  {
    UsuarioDto registrado = new UsuarioDto("id-1", "tg_555", TipoRol.USER, null);
    when(authService.registrar(any())).thenReturn(registrado);

    UsuarioDto vinculado = new UsuarioDto("id-1", "tg_555", TipoRol.USER,
        new MedioContacto("555", TipoMedioContacto.TELEGRAM));
    when(authService.actualizarContacto(eq("id-1"), any())).thenReturn(vinculado);

    AltaNativaHandler handler = new AltaNativaHandler(authService);

    // 555L es el remitenteId (Message.from().id()), no el chatId: en un grupo
    // dos personas sin vincular comparten chatId pero nunca remitenteId, asi
    // que no pueden terminar con el mismo username "tg_<...>".
    UsuarioDto resultado = handler.crearUsuarioNativo(555L);

    ArgumentCaptor<RegistroRequest> registroCaptor = ArgumentCaptor.forClass(RegistroRequest.class);
    org.mockito.Mockito.verify(authService).registrar(registroCaptor.capture());
    assertThat(registroCaptor.getValue().username()).isEqualTo("tg_555");
    assertThat(registroCaptor.getValue().password()).isNotBlank();

    ArgumentCaptor<MedioContacto> medioCaptor = ArgumentCaptor.forClass(MedioContacto.class);
    org.mockito.Mockito.verify(authService).actualizarContacto(eq("id-1"), medioCaptor.capture());
    assertThat(medioCaptor.getValue().getValor()).isEqualTo("555");
    assertThat(medioCaptor.getValue().getTipo()).isEqualTo(TipoMedioContacto.TELEGRAM);

    assertThat(resultado).isEqualTo(vinculado);
  }
}
