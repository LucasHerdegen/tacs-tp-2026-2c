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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(
        webEnvironment =
                SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "security.jwt.secret=test-secret-key-with-at-least-32-bytes"
)
class ActividadesMeIntegrationTests {
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\"token\":\"([^\"]+)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ActividadesRepository actividadesRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private Usuario yo;
    private Usuario otro;
    private String tokenYo;

    @BeforeEach
    void setUp() throws Exception
    {
        actividadesRepository.deleteAll();
        usuarioRepository.deleteAll();

        yo = usuarioRepository.save(new Usuario("yo", passwordEncoder.encode("password-segura"), TipoRol.USER));
        otro = usuarioRepository.save(new Usuario("otro", passwordEncoder.encode("password-segura"), TipoRol.USER));

        tokenYo = login("yo", "password-segura");
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception
    {
        HttpResponse<String> response = get("/api/actividades/me", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void organizadorTrueDevuelveSoloLasQueOrganizoYo() throws Exception
    {
        guardarActividad("Asado que organizo", yo);
        guardarActividad("Partido de otro", otro);

        HttpResponse<String> response = get("/api/actividades/me?organizador=true", tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Asado que organizo");
        assertThat(response.body()).doesNotContain("Partido de otro");
    }

    /**
     * El constructor de Actividad agrega al organizador como participante, asi que
     * "participadas" incluye tambien las que organizo yo. El caso negativo real es
     * una actividad ajena a la que no me sume.
     */
    @Test
    void organizadorFalseDevuelveAquellasEnLasQueParticipo() throws Exception
    {
        Actividad ajena = guardarActividad("Salida a la que me sumo", otro);
        ajena.agregarParticipante(yo);
        actividadesRepository.save(ajena);

        guardarActividad("Corrida que organizo", yo);
        guardarActividad("Asado ajeno al que no voy", otro);

        HttpResponse<String> response = get("/api/actividades/me?organizador=false", tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Salida a la que me sumo");
        assertThat(response.body()).contains("Corrida que organizo");
        assertThat(response.body()).doesNotContain("Asado ajeno al que no voy");
    }

    @Test
    void sinOrganizadorDevuelveLaUnionDeOrganizadasYParticipadas() throws Exception
    {
        guardarActividad("La organizo yo", yo);

        Actividad ajena = guardarActividad("Me sumo a esta", otro);
        ajena.agregarParticipante(yo);
        actividadesRepository.save(ajena);

        guardarActividad("No tiene nada que ver conmigo", otro);

        HttpResponse<String> response = get("/api/actividades/me", tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("La organizo yo");
        assertThat(response.body()).contains("Me sumo a esta");
        assertThat(response.body()).doesNotContain("No tiene nada que ver conmigo");
    }

    @Test
    void usuarioSinActividadesDevuelveListaVacia() throws Exception
    {
        HttpResponse<String> response = get("/api/actividades/me", tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[]");
    }

    @Test
    void combinaFiltroDeEstadoConOrganizador() throws Exception
    {
        Actividad enPropuesta = guardarActividad("Asado en propuesta", yo);
        enPropuesta.setEstado(TipoEstadoActividad.PROPUESTA);
        actividadesRepository.save(enPropuesta);

        Actividad confirmadaAct = guardarActividad("Asado confirmado", yo);
        confirmadaAct.setEstado(TipoEstadoActividad.CONFIRMADA);
        actividadesRepository.save(confirmadaAct);

        HttpResponse<String> response = get("/api/actividades/me?organizador=true&estado=CONFIRMADA", tokenYo);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Asado confirmado");
        assertThat(response.body()).doesNotContain("Asado en propuesta");
    }

    private Actividad guardarActividad(String titulo, Usuario organizador)
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
                organizador);

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