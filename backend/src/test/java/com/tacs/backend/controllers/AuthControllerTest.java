package com.tacs.backend.controllers;

import com.tacs.backend.config.SecurityConfig;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.dtos.auth.LoginRequest;
import com.tacs.backend.dtos.auth.LoginResponse;
import com.tacs.backend.dtos.auth.RegistroRequest;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.exceptions.InvalidCredentialsException;
import com.tacs.backend.exceptions.UsernameAlreadyExistsException;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.AuthService;
import com.tacs.backend.services.JwtService;
import com.tacs.backend.services.implem.JwtServiceImplem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtServiceImplem.class})
@TestPropertySource(properties = {
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes",
    "security.jwt.expiration-seconds=3600"
})
class AuthControllerTest
{
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private UsuarioRepository usuarioRepository;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private CacheManager cacheManager;

  @Test
  void loginCorrectoDevuelveTokenYStatusOk() throws Exception
  {
    LoginResponse loginResponse = new LoginResponse("mocked-jwt-token", "Bearer", 3600L);
    when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

    String body = "{\"username\":\"santi\",\"password\":\"password-segura\"}";

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("mocked-jwt-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(3600));
  }

  @Test
  void loginConCredencialesInvalidasDevuelveUnauthorized() throws Exception
  {
    when(authService.login(any(LoginRequest.class)))
        .thenThrow(new InvalidCredentialsException("Credenciales invalidas"));

    String body = "{\"username\":\"santi\",\"password\":\"password-incorrecta\"}";

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Credenciales invalidas"));
  }

  @Test
  void loginConCamposVaciosDevuelveBadRequest() throws Exception
  {
    String body = "{\"username\":\"\",\"password\":\"\"}";

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registerCorrectoDevuelveUsuarioDtoYStatusCreated() throws Exception
  {
    UsuarioDto usuarioDto = new UsuarioDto("usr-1", "nuevo_usuario", TipoRol.USER, null);
    when(authService.registrar(any(RegistroRequest.class))).thenReturn(usuarioDto);

    String body = "{\"username\":\"nuevo_usuario\",\"password\":\"password-segura\"}";

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("usr-1"))
        .andExpect(jsonPath("$.username").value("nuevo_usuario"))
        .andExpect(jsonPath("$.rol").value("USER"));
  }

  @Test
  void registerConUsernameExistenteDevuelveConflict() throws Exception
  {
    when(authService.registrar(any(RegistroRequest.class)))
        .thenThrow(new UsernameAlreadyExistsException("El username ya esta registrado"));

    String body = "{\"username\":\"usuario_existente\",\"password\":\"password-segura\"}";

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("El username ya esta registrado"));
  }

  @Test
  void registerConCamposVaciosDevuelveBadRequest() throws Exception
  {
    String body = "{\"username\":\"\",\"password\":\"\"}";

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }
}
