package com.tacs.backend.services.implem;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplemTest
{
  private static final Long USUARIO_ID = 1L;

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtService jwtService;

  @InjectMocks
  private AuthServiceImplem service;

  @Test
  void actualizarContactoPersisteElUsuarioConElNuevoMedioDeContacto()
  {
    Usuario usuario = crearUsuario();
    MedioContacto nuevoContacto = new MedioContacto("123456789", TipoMedioContacto.TELEGRAM);

    when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.save(usuario)).thenReturn(usuario);

    service.actualizarContacto(USUARIO_ID, nuevoContacto);

    ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
    verify(usuarioRepository).save(guardado.capture());

    assertThat(guardado.getValue().getMedioContacto()).isEqualTo(nuevoContacto);
  }

  @Test
  void actualizarContactoDevuelveElContactoActualizadoEnElDto()
  {
    Usuario usuario = crearUsuario();
    MedioContacto nuevoContacto = new MedioContacto("123456789", TipoMedioContacto.TELEGRAM);

    when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
    when(usuarioRepository.save(usuario)).thenReturn(usuario);

    var dto = service.actualizarContacto(USUARIO_ID, nuevoContacto);

    assertThat(dto.medioContacto()).isEqualTo(nuevoContacto);
  }

  @Test
  void actualizarContactoDeUsuarioInexistenteLanzaExcepcionYNoGuardaNada()
  {
    when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() ->
        service.actualizarContacto(USUARIO_ID, new MedioContacto("123", TipoMedioContacto.TELEGRAM)))
        .isInstanceOf(UsuarioNotFoundException.class);
  }

  private Usuario crearUsuario()
  {
    Usuario usuario = new Usuario("participante", "hash", TipoRol.USER);
    usuario.setId(USUARIO_ID);
    return usuario;
  }
}
