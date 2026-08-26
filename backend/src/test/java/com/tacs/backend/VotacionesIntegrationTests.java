package com.tacs.backend;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
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
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.jwt.secret=test-secret-key-with-at-least-32-bytes")
class VotacionesIntegrationTests
{
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");

    @LocalServerPort
    private int port;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ActividadesRepository actividadesRepository;

    @Autowired
    private VotacionesRepository votacionesRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Usuario yo;
    private Usuario otro;
    private String tokenYo;

    @BeforeEach
    void setUp() throws Exception
    {
        votacionesRepository.deleteAll();
        actividadesRepository.deleteAll();
        usuarioRepository.deleteAll();

        yo = usuarioRepository.save(new Usuario("yo", passwordEncoder.encode("password-segura"), TipoRol.USER));
        otro = usuarioRepository.save(new Usuario("otro", passwordEncoder.encode("password-segura"), TipoRol.USER));

        tokenYo = login("yo", "password-segura");
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception
    {
        HttpResponse<String> response = get("/api/votaciones", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void abiertaTrueDevuelveSoloVotacionesAbiertasDelUsuarioAutenticado() throws Exception
    {
        Actividad miActividad = guardarActividad("Actividad que organizo", yo);
        guardarVotacion(miActividad, true);

        Actividad ajena = guardarActividad("Actividad de otro", otro);
        guardarVotacion(ajena, true);

        HttpResponse<String> response = get("/api/votaciones?abierta=true", tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Actividad que organizo");
        assertThat(response.body()).doesNotContain("Actividad de otro");
    }

    @Test
    void abiertaFalseDevuelveSoloLasCerradas() throws Exception
    {
        Actividad miActividad = guardarActividad("Actividad cerrada", yo);
        guardarVotacion(miActividad, false);

        Actividad otraAbierta = guardarActividad("Actividad abierta", yo);
        guardarVotacion(otraAbierta, true);

        HttpResponse<String> response = get("/api/votaciones?abierta=false", tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Actividad cerrada");
        assertThat(response.body()).doesNotContain("Actividad abierta");
    }

    @Test
    void incluyeVotacionesDeActividadesEnLasQueSoyParticipante() throws Exception
    {
        Actividad ajena = guardarActividad("Actividad a la que me sumo", otro);
        ajena.agregarParticipante(yo);
        actividadesRepository.save(ajena);
        guardarVotacion(ajena, true);

        HttpResponse<String> response = get("/api/votaciones?abierta=true", tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Actividad a la que me sumo");
    }

    @Test
    void usuarioSinVotacionesDevuelveListaVacia() throws Exception
    {
        HttpResponse<String> response = get("/api/votaciones?abierta=true", tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[]");
    }

    @Test
    void pasarUsuarioIdComoQueryParamNoPermiteVerElDeOtroUsuario() throws Exception
    {
        Actividad ajena = guardarActividad("Actividad de otro", otro);
        guardarVotacion(ajena, true);

        HttpResponse<String> response = get("/api/votaciones?abierta=true&usuarioId=" + otro.getId(), tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("Actividad de otro");
    }

    private Actividad guardarActividad(String titulo, Usuario organizador)
    {
        Actividad actividad = new Actividad(
                titulo,
                "descripcion",
                TipoActividad.AIRE_LIBRE,
                new Ubicacion("Palermo", -34.58, -58.43),
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now(),
                2,
                10,
                organizador);

        return actividadesRepository.save(actividad);
    }

    private void guardarVotacion(Actividad actividad, boolean abierta)
    {
        Votacion votacion = new Votacion();
        votacion.setActividad(actividad);
        votacion.setAbierta(abierta);
        votacion.setFechaApertura(LocalDateTime.now());
        votacion.setQuorumMinimo(1);

        votacionesRepository.save(votacion);
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