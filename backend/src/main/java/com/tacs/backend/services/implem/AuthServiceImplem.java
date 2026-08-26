package com.tacs.backend.services.implem;

import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.auth.LoginRequest;
import com.tacs.backend.dtos.auth.LoginResponse;
import com.tacs.backend.dtos.auth.RegistroRequest;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.exceptions.InvalidCredentialsException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.UsernameAlreadyExistsException;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.AuthService;
import com.tacs.backend.services.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
class AuthServiceImplem implements AuthService
{
  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  @Override
  @Transactional
  public UsuarioDto registrar(RegistroRequest request)
  {
    if (usuarioRepository.existsByUsername(request.username()))
      throw new UsernameAlreadyExistsException("El username ya esta registrado");

    String passwordHash = passwordEncoder.encode(request.password());
    Usuario usuario = new Usuario(request.username(), passwordHash, TipoRol.USER);
    Usuario usuarioGuardado = usuarioRepository.save(usuario);

    return new UsuarioDto(usuarioGuardado.getId(), usuarioGuardado.getUsername(), usuarioGuardado.getRol());
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
  @Transactional
  public UsuarioDto actualizarRol(Long usuarioId, TipoRol rol)
  {
    Usuario usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

    usuario.setRol(rol);
    return toDto(usuario);
  }

  private UsuarioDto toDto(Usuario usuario)
  {
    return new UsuarioDto(usuario.getId(), usuario.getUsername(), usuario.getRol());
  }
}
