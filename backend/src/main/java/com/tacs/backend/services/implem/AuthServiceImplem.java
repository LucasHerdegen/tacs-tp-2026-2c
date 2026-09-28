package com.tacs.backend.services.implem;

import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.auth.LoginRequest;
import com.tacs.backend.dtos.auth.LoginResponse;
import com.tacs.backend.dtos.auth.RegistroRequest;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.exceptions.InvalidCredentialsException;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.CambioRolConcurrenteException;
import com.tacs.backend.exceptions.UltimoAdministradorException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.UsernameAlreadyExistsException;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.AuthService;
import com.tacs.backend.services.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import com.tacs.backend.domain.usuario.MedioContacto;

import java.util.List;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
@Service
class AuthServiceImplem implements AuthService
{
  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LockProvider lockProvider;

  @Override
  @Transactional
  public UsuarioDto registrar(RegistroRequest request)
  {
    if (usuarioRepository.existsByUsername(request.username()))
      throw new UsernameAlreadyExistsException("El username ya esta registrado");

    String passwordHash = passwordEncoder.encode(request.password());
    Usuario usuario = new Usuario(request.username(), passwordHash, TipoRol.USER);
    Usuario usuarioGuardado = usuarioRepository.save(usuario);

    return toDto(usuarioGuardado);
  }

  @Override
  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request)
  {
    Usuario usuario = usuarioRepository.findByUsername(request.username())
        .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas"));

    if (!passwordEncoder.matches(request.password(), usuario.getPassword()))
      throw new InvalidCredentialsException("Credenciales invalidas");

    String token = jwtService.generarToken(usuario);
    return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
  }

  @Override
  @Transactional(readOnly = true)
  public UsuarioDto buscarPorUsername(String username)
  {
    Usuario usuario = usuarioRepository.findByUsername(username)
        .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

    return toDto(usuario);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UsuarioDto> listarUsuarios()
  {
    return usuarioRepository.findAll().stream()
        .map(this::toDto)
        .toList();
  }

  @Override
  @Transactional
  public UsuarioDto actualizarRol(String usuarioId, TipoRol rol, String actorId)
  {
    LockConfiguration config = new LockConfiguration(
        Instant.now(), "usuarios_actualizar_rol", Duration.ofMinutes(1), Duration.ZERO);
    SimpleLock lock = lockProvider.lock(config)
        .orElseThrow(() -> new CambioRolConcurrenteException("Hay otro cambio de rol en curso"));

    try
    {
      Usuario actor = usuarioRepository.findById(actorId)
          .orElseThrow(() -> new AccesoDenegadoException("No tenés permisos para cambiar roles"));
      if (actor.getRol() != TipoRol.ADMIN)
        throw new AccesoDenegadoException("No tenés permisos para cambiar roles");

      Usuario usuario = usuarioRepository.findById(usuarioId)
          .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));
      if (usuario.getId().equals(actorId))
        throw new AccesoDenegadoException("No podés cambiar tu propio rol");
      if (usuario.getRol() == TipoRol.ADMIN && rol != TipoRol.ADMIN &&
          usuarioRepository.countByRol(TipoRol.ADMIN) <= 1)
        throw new UltimoAdministradorException("No se puede quitar el último administrador");

      usuario.setRol(rol);
      return toDto(usuarioRepository.save(usuario));
    }
    finally
    {
      lock.unlock();
    }
  }

  @Override
  public UsuarioDto obtenerUsuario(String usuarioId)
  {
    Usuario usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));
    return toDto(usuario);
  }

  @Override
  @Transactional
  public UsuarioDto actualizarContacto(String usuarioId, MedioContacto medioContacto)
  {
    Usuario usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));
    usuario.setMedioContacto(medioContacto);
    Usuario usuarioGuardado = usuarioRepository.save(usuario);
    return toDto(usuarioGuardado);
  }

  private UsuarioDto toDto(Usuario usuario)
  {
    return new UsuarioDto(usuario.getId(), usuario.getUsername(), usuario.getRol(), usuario.getMedioContacto());
  }
}
