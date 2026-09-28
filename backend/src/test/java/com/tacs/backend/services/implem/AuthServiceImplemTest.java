package com.tacs.backend.services.implem;

import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.CambioRolConcurrenteException;
import com.tacs.backend.exceptions.UltimoAdministradorException;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplemTest
{
  private static final String USUARIO_ID = "1";

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtService jwtService;

  @Mock
  private LockProvider lockProvider;

  @Mock
  private SimpleLock roleLock;

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

  @Test
  void noPermiteCambiarElPropioRolYLiberaElBloqueo()
  {
    Usuario admin = new Usuario("admin", "hash", TipoRol.ADMIN);
    admin.setId("admin-id");
    when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(roleLock));
    when(usuarioRepository.findById("admin-id")).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.actualizarRol("admin-id", TipoRol.USER, "admin-id"))
        .isInstanceOf(AccesoDenegadoException.class);
    verify(roleLock).unlock();
  }

  @Test
  void rechazaCambiosSiElActorYaNoEsAdministrador()
  {
    Usuario actor = crearUsuario();
    when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(roleLock));
    when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(actor));

    assertThatThrownBy(() -> service.actualizarRol("otro-id", TipoRol.ADMIN, USUARIO_ID))
        .isInstanceOf(AccesoDenegadoException.class);
    verify(roleLock).unlock();
  }

  @Test
  void administradorPuedeCambiarElRolDeOtroUsuario()
  {
    Usuario actor = new Usuario("admin", "hash", TipoRol.ADMIN);
    actor.setId("admin-id");
    Usuario objetivo = crearUsuario();
    when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(roleLock));
    when(usuarioRepository.findById("admin-id")).thenReturn(Optional.of(actor));
    when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(objetivo));
    when(usuarioRepository.save(objetivo)).thenReturn(objetivo);

    var resultado = service.actualizarRol(USUARIO_ID, TipoRol.ADMIN, "admin-id");

    assertThat(resultado.rol()).isEqualTo(TipoRol.ADMIN);
    verify(usuarioRepository).save(objetivo);
    verify(roleLock).unlock();
  }

  @Test
  void rechazaQuitarElUltimoAdministrador()
  {
    Usuario actor = new Usuario("actor", "hash", TipoRol.ADMIN);
    actor.setId("actor-id");
    Usuario objetivo = new Usuario("objetivo", "hash", TipoRol.ADMIN);
    objetivo.setId("objetivo-id");
    when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(roleLock));
    when(usuarioRepository.findById("actor-id")).thenReturn(Optional.of(actor));
    when(usuarioRepository.findById("objetivo-id")).thenReturn(Optional.of(objetivo));
    when(usuarioRepository.countByRol(TipoRol.ADMIN)).thenReturn(1L);

    assertThatThrownBy(() -> service.actualizarRol("objetivo-id", TipoRol.USER, "actor-id"))
        .isInstanceOf(UltimoAdministradorException.class);
    verify(roleLock).unlock();
  }

  @Test
  void rechazaOtroCambioDeRolMientrasHayUnoEnCurso()
  {
    when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.actualizarRol("objetivo-id", TipoRol.USER, "actor-id"))
        .isInstanceOf(CambioRolConcurrenteException.class);
  }

  private Usuario crearUsuario()
  {
    Usuario usuario = new Usuario("participante", "hash", TipoRol.USER);
    usuario.setId(USUARIO_ID);
    return usuario;
  }
}
