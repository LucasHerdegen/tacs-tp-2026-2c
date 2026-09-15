package com.tacs.backend;

import com.tacs.backend.persistence.entities.TelegramVinculacionEntity;
import com.tacs.backend.persistence.repositories.TelegramVinculacionMongoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes",
    "weatherapi.api-key=test-key",
    "telegram.bot-token=test-token",
    "telegram.bot-username=test_bot"
})
@AutoConfigureMockMvc
class TelegramVinculacionIntegrationTests
{
  private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private TelegramVinculacionMongoRepository vinculacionRepository;

  @BeforeEach
  void setUp()
  {
    mongoTemplate.getDb().drop();
  }

  @AfterEach
  void tearDown()
  {
    mongoTemplate.getDb().drop();
  }

  @Test
  void generaUnTokenYUnDeepLinkEInvalidaElTokenAnteriorAlPedirOtro() throws Exception
  {
    String jwt = registrarYLoguear("santi", "password-segura");

    MvcResult primeraRespuesta = pedirVinculacion(jwt);
    assertThat(primeraRespuesta.getResponse().getStatus()).isEqualTo(200);
    String primerBody = primeraRespuesta.getResponse().getContentAsString();
    assertThat(primerBody).contains("\"deepLink\":\"https://t.me/test_bot?start=");
    String primerToken = extraerToken(primerBody);

    MvcResult segundaRespuesta = pedirVinculacion(jwt);
    assertThat(segundaRespuesta.getResponse().getStatus()).isEqualTo(200);
    String segundoToken = extraerToken(segundaRespuesta.getResponse().getContentAsString());

    assertThat(segundoToken).isNotEqualTo(primerToken);

    List<TelegramVinculacionEntity> todos = vinculacionRepository.findAll();
    TelegramVinculacionEntity primeraEntidad = todos.stream()
        .filter(v -> v.getToken().equals(primerToken))
        .findFirst()
        .orElseThrow();
    TelegramVinculacionEntity segundaEntidad = todos.stream()
        .filter(v -> v.getToken().equals(segundoToken))
        .findFirst()
        .orElseThrow();

    assertThat(primeraEntidad.isUsado()).isTrue();
    assertThat(segundaEntidad.isUsado()).isFalse();
  }

  @Test
  void sinAutenticacionDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(post("/api/usuarios/me/telegram/vinculacion"))
        .andExpect(status().isUnauthorized());
  }

  private MvcResult pedirVinculacion(String jwt) throws Exception
  {
    return mockMvc.perform(post("/api/usuarios/me/telegram/vinculacion")
            .header("Authorization", "Bearer " + jwt))
        .andReturn();
  }

  private String registrarYLoguear(String username, String password) throws Exception
  {
    String credenciales = """
        {"username":"%s","password":"%s"}
        """.formatted(username, password).trim();

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(credenciales))
        .andExpect(status().isCreated());

    MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(credenciales))
        .andReturn();

    return extraerToken(loginResult.getResponse().getContentAsString());
  }

  private String extraerToken(String responseBody)
  {
    Matcher matcher = TOKEN_PATTERN.matcher(responseBody);
    assertThat(matcher.find()).as("La respuesta debe contener un token").isTrue();
    return matcher.group(1);
  }
}
