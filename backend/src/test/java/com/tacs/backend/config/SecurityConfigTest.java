package com.tacs.backend.config;

import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest
{
  private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
  private final SecurityConfig securityConfig = new SecurityConfig();

  @Test
  void usaElRolActualDeLaBaseAunqueElTokenTengaUnRolAnterior()
  {
    Usuario usuario = new Usuario("admin", "hash", TipoRol.USER);
    usuario.setId("usuario-id");
    when(usuarioRepository.findById("usuario-id")).thenReturn(Optional.of(usuario));

    var authentication = securityConfig.jwtAuthenticationConverter(usuarioRepository).convert(token("admin"));

    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_USER");
  }

  @Test
  void rechazaUnTokenSiElUsuarioYaNoExiste()
  {
    when(usuarioRepository.findById("usuario-id")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> securityConfig.jwtAuthenticationConverter(usuarioRepository)
        .convert(token("admin")))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  private Jwt token(String username)
  {
    return Jwt.withTokenValue("token")
        .header("alg", "HS256")
        .subject(username)
        .claim("id", "usuario-id")
        .claim("role", "ADMIN")
        .build();
  }
}
