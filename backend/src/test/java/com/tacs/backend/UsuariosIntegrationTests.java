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
class UsuariosIntegrationTests
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
    usuarioRepository.save(new Usuario("admin", passwordEncoder.encode("password-segura"), TipoRol.ADMIN));
    usuarioRepository.save(new Usuario("usuario", passwordEncoder.encode("password-segura"), TipoRol.USER));
  }

  @Test
  void administradorPuedeListarUsuariosSinExponerPasswords() throws Exception
  {
    String token = login("admin");

    HttpResponse<String> response = getUsuarios(token);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"username\":\"admin\"");
    assertThat(response.body()).contains("\"username\":\"usuario\"");
    assertThat(response.body()).contains("\"rol\":\"ADMIN\"");
    assertThat(response.body()).contains("\"rol\":\"USER\"");
    assertThat(response.body()).doesNotContain("password-segura");
  }

  @Test
  void usuarioComunNoPuedeListarUsuarios() throws Exception
  {
    String token = login("usuario");

    HttpResponse<String> response = getUsuarios(token);

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void usuarioNoAutenticadoNoPuedeListarUsuarios() throws Exception
  {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri("/api/usuarios"))
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(401);
  }

  @Test
  void administradorPuedeCambiarElRolDeUnUsuario() throws Exception
  {
    String token = login("admin");
    Usuario targetUser = usuarioRepository.findByUsername("usuario").orElseThrow();

    HttpResponse<String> response = patchRole(targetUser.getId(), "ADMIN", token);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"username\":\"usuario\"");
    assertThat(response.body()).contains("\"rol\":\"ADMIN\"");
    assertThat(usuarioRepository.findById(targetUser.getId()).orElseThrow().getRol()).isEqualTo(TipoRol.ADMIN);
  }

  @Test
  void usuarioComunNoPuedeCambiarRoles() throws Exception
  {
    String token = login("usuario");
    Usuario admin = usuarioRepository.findByUsername("admin").orElseThrow();

    HttpResponse<String> response = patchRole(admin.getId(), "USER", token);

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(usuarioRepository.findById(admin.getId()).orElseThrow().getRol()).isEqualTo(TipoRol.ADMIN);
  }

  private HttpResponse<String> getUsuarios(String token) throws Exception
  {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri("/api/usuarios"))
        .header("Authorization", "Bearer " + token)
        .GET()
        .build();

    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> patchRole(Long userId, String role, String token) throws Exception
  {
    String body = """
        {"rol":"%s"}
        """.formatted(role).trim();

    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri("/api/usuarios/" + userId + "/rol"))
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "application/json")
        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
        .build();

    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String login(String username) throws Exception
  {
    String body = """
        {"username":"%s","password":"password-segura"}
        """.formatted(username).trim();

    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri("/api/auth/login"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    Matcher matcher = TOKEN_PATTERN.matcher(response.body());
    assertThat(matcher.find()).as("La respuesta debe contener un token").isTrue();
    return matcher.group(1);
  }

  private URI uri(String path)
  {
    return URI.create("http://localhost:" + port + path);
  }
}
