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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(properties = "security.jwt.secret=test-secret-key-with-at-least-32-bytes")
@AutoConfigureMockMvc
@Transactional
class ActividadesMeIntegrationTests {
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("\"token\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ActividadesRepository actividadesRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario yo;
    private Usuario otro;
    private String tokenYo;

    @BeforeEach
    void setUp() throws Exception
    {
        yo = usuarioRepository.save(new Usuario("yo", passwordEncoder.encode("password-segura"), TipoRol.USER));
        otro = usuarioRepository.save(new Usuario("otro", passwordEncoder.encode("password-segura"), TipoRol.USER));

        tokenYo = login("yo", "password-segura");
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception
    {
        mockMvc.perform(get("/api/actividades/me"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void organizadorTrueDevuelveSoloLasQueOrganizoYo() throws Exception
    {
        guardarActividad("Asado que organizo", yo);
        guardarActividad("Partido de otro", otro);

        MvcResult result = mockMvc.perform(get("/api/actividades/me?organizador=true")
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Asado que organizo");
        assertThat(body).doesNotContain("Partido de otro");
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

        MvcResult result = mockMvc.perform(get("/api/actividades/me?organizador=false")
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Salida a la que me sumo");
        assertThat(body).contains("Corrida que organizo");
        assertThat(body).doesNotContain("Asado ajeno al que no voy");
    }

    @Test
    void sinOrganizadorDevuelveLaUnionDeOrganizadasYParticipadas() throws Exception
    {
        guardarActividad("La organizo yo", yo);

        Actividad ajena = guardarActividad("Me sumo a esta", otro);
        ajena.agregarParticipante(yo);
        actividadesRepository.save(ajena);

        guardarActividad("No tiene nada que ver conmigo", otro);

        MvcResult result = mockMvc.perform(get("/api/actividades/me")
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("La organizo yo");
        assertThat(body).contains("Me sumo a esta");
        assertThat(body).doesNotContain("No tiene nada que ver conmigo");
    }

    @Test
    void usuarioSinActividadesDevuelveListaVacia() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/api/actividades/me")
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).isEqualTo("[]");
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

        MvcResult result = mockMvc.perform(get("/api/actividades/me?organizador=true&estado=CONFIRMADA")
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Asado confirmado");
        assertThat(body).doesNotContain("Asado en propuesta");
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

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .content(body))
                                  .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Matcher matcher = TOKEN_PATTERN.matcher(responseBody);
        assertThat(matcher.find()).as("La respuesta de login debe contener un token").isTrue();
        return matcher.group(1);
    }
}
