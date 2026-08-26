package com.tacs.backend.config;

import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
class AdminInitializer implements ApplicationRunner
{
  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${security.admin.username:}")
  private String username;

  @Value("${security.admin.password:}")
  private String password;

  @Override
  public void run(ApplicationArguments args)
  {
    if (username.isBlank() || password.isBlank() || usuarioRepository.existsByUsername(username))
      return;

    Usuario admin = new Usuario(username, passwordEncoder.encode(password), TipoRol.ADMIN);
    usuarioRepository.save(admin);
  }
}
