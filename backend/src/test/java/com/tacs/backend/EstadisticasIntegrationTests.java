package com.tacs.backend;

import com.tacs.backend.domain.actividad.*;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.jwt.secret=test-secret-key-with-at-least-32-bytes"
)
// TODO: sumar un test unitario de EstadisticasServiceImplem con ActividadesRepository mockeado
class EstadisticasIntegrationTests
{
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ActividadesRepository actividadesRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Usuario admin;
    private String tokenAdmin;
    private String tokenUser;

    @BeforeEach
    void setUp() throws Exception
    {
        try(Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertThat(url).as("La suite de tests solo puede correr contra H2 en memoria")
                    .startsWith("jdbc:h2:mem:");
        }
        actividadesRepository.deleteAll();
        usuarioRepository.deleteAll();

        admin = usuarioRepository.save(new Usuario("admin", passwordEncoder.encode("password-segura"), TipoRol.ADMIN));
        usuarioRepository.save(new Usuario("user", passwordEncoder.encode("password-segura"), TipoRol.USER));

        tokenAdmin = login("admin", "password-segura");
        tokenUser = login("user", "password-segura");
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception
    {
        HttpResponse<String> response = get("/api/admin/estadisticas", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void usuarioSinRolAdminDevuelveForbidden() throws Exception
    {
        HttpResponse<String> response = get("/api/admin/estadisticas", tokenUser);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void administradorObtieneLosConteos() throws Exception
    {
        guardarActividad("Asado propuesto");

        Actividad actividadReprogramada = guardarActividad("Salida reprogramada");
        actividadReprogramada.setEstado(TipoEstadoActividad.REPROGRAMADA);
        actividadesRepository.save(actividadReprogramada);

        Actividad actividadCancelada = guardarActividad("Corrida cancelada");
        actividadCancelada.setEstado(TipoEstadoActividad.CANCELADA);
        actividadesRepository.save(actividadCancelada);

        Actividad actividadConfirmada = guardarActividad("Juntada Confirmada");
        actividadConfirmada.setEstado(TipoEstadoActividad.CONFIRMADA);
        actividadesRepository.save(actividadConfirmada);

        Actividad actividadFinalizada = guardarActividad("Partido terminado");
        actividadFinalizada.setEstado(TipoEstadoActividad.FINALIZADA);
        actividadesRepository.save(actividadFinalizada);

        HttpResponse<String> response = get("/api/admin/estadisticas", tokenAdmin);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"actividadesCreadas\":5");
        assertThat(response.body()).contains("\"actividadesReprogramadas\":1");
        assertThat(response.body()).contains("\"actividadesCanceladas\":1");
        assertThat(response.body()).contains("\"actividadesConfirmadas\":1");
        assertThat(response.body()).contains("\"actividadesFinalizadas\":1");
    }

    private Actividad guardarActividad(String titulo)
    {
        Actividad actividad = new Actividad(
                titulo,
                "descripcion",
                TipoActividad.AIRE_LIBRE,
                new Ubicacion("Palermo", -34.58, -58.43),
                LocalDateTime.now().plusDays(1),
                120,
                LocalDateTime.now(),
                2,
                10,
                admin);

        return actividadesRepository.save(actividad);
    }

    private String login(String username, String password) throws Exception
    {
        String body = """
        {"username":"%s","password":"%s"}
        """.formatted(username, password).trim();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = send(request);
        Matcher matcher = TOKEN_PATTERN.matcher(response.body());
        assertThat(matcher.find()).as("La respuesta de login debe contener un token").isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> get(String path, String token) throws Exception
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri(path)).GET();
        if (token != null)
            builder.header("Authorization", "Bearer " + token);

        return send(builder.build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception
    {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path)
    {
        return URI.create("http://localhost:" + port + path);
    }
}