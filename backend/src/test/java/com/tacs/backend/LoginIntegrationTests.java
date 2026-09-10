package com.tacs.backend;

import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "security.jwt.secret=test-secret-key-with-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class LoginIntegrationTests
{
  private static final Pattern TOKEN_PATTERN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UsuarioRepository usuarioRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp()
  {
    usuarioRepository.save(new Usuario(
        "santi",
        passwordEncoder.encode("password-segura"),
        TipoRol.USER));
  }

  @Test
  void loginCorrectoDevuelveUnJwtQuePermiteIdentificarAlUsuario() throws Exception
  {
    MvcResult loginResult = postLogin("santi", "password-segura");
    
    assertThat(loginResult.getResponse().getStatus()).isEqualTo(200);
    String loginBody = loginResult.getResponse().getContentAsString();
    assertThat(loginBody).contains("\"tokenType\":\"Bearer\"");
    assertThat(loginBody).contains("\"expiresIn\":3600");

    String token = extractToken(loginBody);
    assertThat(token.split("\\.")).hasSize(3);

    MvcResult meResult = mockMvc.perform(get("/api/auth/me")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn();

    String meBody = meResult.getResponse().getContentAsString();
    assertThat(meBody).contains("\"username\":\"santi\"");
    assertThat(meBody).contains("\"rol\":\"USER\"");
    assertThat(meBody).doesNotContain("password-segura");
  }

  @Test
  void loginConPasswordIncorrectaDevuelveUnauthorized() throws Exception
  {
    MvcResult result = postLogin("santi", "password-incorrecta");

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(result.getResponse().getContentAsString()).isEqualTo("Credenciales invalidas");
  }

  @Test
  void loginConUsuarioInexistenteDevuelveElMismoError() throws Exception
  {
    MvcResult result = postLogin("usuario-inexistente", "password-segura");

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(result.getResponse().getContentAsString()).isEqualTo("Credenciales invalidas");
  }

  @Test
  void loginConCamposVaciosDevuelveBadRequest() throws Exception
  {
    MvcResult result = postLogin("", "");

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
  }

  private MvcResult postLogin(String username, String password) throws Exception
  {
    String body = """
        {"username":"%s","password":"%s"}
        """.formatted(username, password).trim();

    return mockMvc.perform(post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
                  .andReturn();
  }

  private String extractToken(String responseBody)
  {
    Matcher matcher = TOKEN_PATTERN.matcher(responseBody);
    assertThat(matcher.find()).as("La respuesta debe contener un token").isTrue();
    return matcher.group(1);
  }
}
