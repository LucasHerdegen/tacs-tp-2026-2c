package com.tacs.backend;

import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "security.jwt.secret=test-secret-key-with-at-least-32-bytes")
class LoginIntegrationTests
{
  private static final Pattern TOKEN_PATTERN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");

  @LocalServerPort
  private int port;

  @Autowired
  private UsuarioRepository usuarioRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeEach
  void setUp()
  {
    usuarioRepository.deleteAll();
    usuarioRepository.save(new Usuario(
        "santi",
        passwordEncoder.encode("password-segura"),
        TipoRol.USER));
  }

  @Test
  void loginCorrectoDevuelveUnJwtQuePermiteIdentificarAlUsuario() throws Exception
  {
    HttpResponse<String> loginResponse = postLogin("santi", "password-segura");

    assertThat(loginResponse.statusCode()).isEqualTo(200);
    assertThat(loginResponse.body()).contains("\"tokenType\":\"Bearer\"");
    assertThat(loginResponse.body()).contains("\"expiresIn\":3600");

    String token = extractToken(loginResponse.body());
    assertThat(token.split("\\.")).hasSize(3);

    HttpRequest meRequest = HttpRequest.newBuilder()
        .uri(uri("/api/auth/me"))
        .header("Authorization", "Bearer " + token)
        .GET()
        .build();

    HttpResponse<String> meResponse = send(meRequest);

    assertThat(meResponse.statusCode()).isEqualTo(200);
    assertThat(meResponse.body()).contains("\"username\":\"santi\"");
    assertThat(meResponse.body()).contains("\"rol\":\"USER\"");
    assertThat(meResponse.body()).doesNotContain("password-segura");
  }

  @Test
  void loginConPasswordIncorrectaDevuelveUnauthorized() throws Exception
  {
    HttpResponse<String> response = postLogin("santi", "password-incorrecta");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).isEqualTo("Credenciales invalidas");
  }

  @Test
  void loginConUsuarioInexistenteDevuelveElMismoError() throws Exception
  {
    HttpResponse<String> response = postLogin("usuario-inexistente", "password-segura");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).isEqualTo("Credenciales invalidas");
  }

  @Test
  void loginConCamposVaciosDevuelveBadRequest() throws Exception
  {
    HttpResponse<String> response = postLogin("", "");

    assertThat(response.statusCode()).isEqualTo(400);
  }

  private HttpResponse<String> postLogin(String username, String password) throws Exception
  {
    String body = """
        {"username":"%s","password":"%s"}
        """.formatted(username, password).trim();

    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri("/api/auth/login"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    return send(request);
  }

  private HttpResponse<String> send(HttpRequest request) throws Exception
  {
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path)
  {
    return URI.create("http://localhost:" + port + path);
  }

  private String extractToken(String responseBody)
  {
    Matcher matcher = TOKEN_PATTERN.matcher(responseBody);
    assertThat(matcher.find()).as("La respuesta debe contener un token").isTrue();
    return matcher.group(1);
  }
}
