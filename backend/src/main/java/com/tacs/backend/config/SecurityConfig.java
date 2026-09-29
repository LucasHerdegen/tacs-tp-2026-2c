package com.tacs.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.Customizer;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Arrays;

@Configuration
public class SecurityConfig
{
  @Value("${cors.allowed-origins:*}")
  private String allowedOrigins;

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    if ("*".equals(allowedOrigins)) {
        configuration.addAllowedOriginPattern("*");
    } else {
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    }
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
    configuration.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private static final String ISSUER = "https://tacs-api";

  @Bean
  public PasswordEncoder passwordEncoder()
  {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, com.tacs.backend.repositories.UsuarioRepository usuarioRepository) throws Exception
  {
    return http
        .cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(
                "/api/auth/register",
                "/api/auth/login",
                "/error",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/api/health")
            .permitAll()
            .requestMatchers(HttpMethod.GET, "/api/usuarios")
            .hasRole("ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/usuarios/*/rol")
            .hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/admin/estadisticas")
            .hasRole("ADMIN")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(usuarioRepository))))
        .build();
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter(com.tacs.backend.repositories.UsuarioRepository usuarioRepository)
  {
    org.springframework.core.convert.converter.Converter<org.springframework.security.oauth2.jwt.Jwt, java.util.Collection<org.springframework.security.core.GrantedAuthority>> authoritiesConverter = jwt -> {
        String userId = jwt.getClaimAsString("id");
        return usuarioRepository.findById(userId)
            .map(u -> (java.util.Collection<org.springframework.security.core.GrantedAuthority>) java.util.List.<org.springframework.security.core.GrantedAuthority>of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + u.getRol().name())))
            .orElseGet(Collections::emptyList);
    };

    JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
    authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
    return authenticationConverter;
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
