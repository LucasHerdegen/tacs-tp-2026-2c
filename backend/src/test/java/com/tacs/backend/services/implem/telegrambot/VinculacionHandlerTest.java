package com.tacs.backend.services.implem.telegrambot;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.exceptions.TelegramVinculacionInvalidaException;
import com.tacs.backend.persistence.entities.TelegramVinculacionEntity;
import com.tacs.backend.persistence.repositories.TelegramVinculacionMongoRepository;
import com.tacs.backend.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VinculacionHandlerTest
{
  @Mock
  private TelegramVinculacionMongoRepository vinculacionRepository;

  @Mock
  private AuthService authService;

  private VinculacionHandler handler;

  @BeforeEach
  void setUp()
  {
    handler = new VinculacionHandler(vinculacionRepository, authService);
  }

  @Test
  void tokenValidoVinculaElChatIdCorrectoYLoMarcaComoUsado()
  {
    TelegramVinculacionEntity vinculacion = new TelegramVinculacionEntity(
        "tok-1", "usr-1", LocalDateTime.now().plusMinutes(10));
    when(vinculacionRepository.findByToken("tok-1")).thenReturn(Optional.of(vinculacion));

    UsuarioDto usuario = new UsuarioDto("usr-1", "santi", TipoRol.USER,
        new MedioContacto("999", TipoMedioContacto.TELEGRAM));
    when(authService.actualizarContacto(eq("usr-1"), any())).thenReturn(usuario);

    UsuarioDto resultado = handler.vincular(999L, "tok-1");

    ArgumentCaptor<MedioContacto> medioCaptor = ArgumentCaptor.forClass(MedioContacto.class);
    verify(authService).actualizarContacto(eq("usr-1"), medioCaptor.capture());
    assertThat(medioCaptor.getValue().getValor()).isEqualTo("999");

    ArgumentCaptor<TelegramVinculacionEntity> guardadoCaptor =
        ArgumentCaptor.forClass(TelegramVinculacionEntity.class);
    verify(vinculacionRepository).save(guardadoCaptor.capture());
    assertThat(guardadoCaptor.getValue().isUsado()).isTrue();

    assertThat(resultado).isEqualTo(usuario);
  }

  @Test
  void tokenVencidoLanzaExcepcionYNoVinculaNada()
  {
    TelegramVinculacionEntity vinculacion = new TelegramVinculacionEntity(
        "tok-1", "usr-1", LocalDateTime.now().minusMinutes(1));
    when(vinculacionRepository.findByToken("tok-1")).thenReturn(Optional.of(vinculacion));

    assertThatThrownBy(() -> handler.vincular(999L, "tok-1"))
        .isInstanceOf(TelegramVinculacionInvalidaException.class);

    verify(authService, never()).actualizarContacto(any(), any());
    verify(vinculacionRepository, never()).save(any());
  }

  @Test
  void tokenYaUsadoLanzaExcepcionYNoVinculaNada()
  {
    TelegramVinculacionEntity vinculacion = new TelegramVinculacionEntity(
        "tok-1", "usr-1", LocalDateTime.now().plusMinutes(10));
    vinculacion.setUsado(true);
    when(vinculacionRepository.findByToken("tok-1")).thenReturn(Optional.of(vinculacion));

    assertThatThrownBy(() -> handler.vincular(999L, "tok-1"))
        .isInstanceOf(TelegramVinculacionInvalidaException.class);

    verify(authService, never()).actualizarContacto(any(), any());
    verify(vinculacionRepository, never()).save(any());
  }

  @Test
  void tokenInexistenteLanzaExcepcionYNoVinculaNada()
  {
    when(vinculacionRepository.findByToken("no-existe")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.vincular(999L, "no-existe"))
        .isInstanceOf(TelegramVinculacionInvalidaException.class);

    verify(authService, never()).actualizarContacto(any(), any());
    verify(vinculacionRepository, never()).save(any());
  }
}
