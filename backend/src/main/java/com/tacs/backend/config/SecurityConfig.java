package com.tacs.backend.config;

import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig
{
  private static final String ISSUER = "https://tacs-api";

  @Bean
  public PasswordEncoder passwordEncoder()
  {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception
  {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(
                "/api/auth/register",
                "/api/auth/login",
                "/error",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**")
            .permitAll()
            .requestMatchers(HttpMethod.GET, "/api/usuarios")
            .hasRole("ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/usuarios/*/rol")
            .hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/admin/estadisticas")
            .hasRole("ADMIN")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .build();
  }

  @Bean
  public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(UsuarioRepository usuarioRepository)
  {
    return jwt -> {
      Object claimId = jwt.getClaim("id");
      Usuario usuario = claimId instanceof String id ? usuarioRepository.findById(id).orElse(null) : null;
      if (usuario == null || !usuario.getUsername().equals(jwt.getSubject()))
        throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Usuario no válido", null));

      return new JwtAuthenticationToken(jwt,
          List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())),
          usuario.getUsername());
    };
  }

  @Bean
  public JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret)
  {
    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

    if (secretBytes.length < 32)
      throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes");

    SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();

    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(ISSUER)));

    return decoder;
  }
}
