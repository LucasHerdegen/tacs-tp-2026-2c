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
class VotacionesIntegrationTests
{
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ActividadesRepository actividadesRepository;

    @Autowired
    private VotacionesRepository votacionesRepository;

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
        mockMvc.perform(get("/api/votaciones"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void abiertaTrueDevuelveSoloVotacionesAbiertasDelUsuarioAutenticado() throws Exception
    {
        Actividad miActividad = guardarActividad("Actividad que organizo", yo);
        guardarVotacion(miActividad, true);

        Actividad ajena = guardarActividad("Actividad de otro", otro);
        guardarVotacion(ajena, true);

        MvcResult result = mockMvc.perform(get("/api/votaciones?abierta=true")
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Actividad que organizo");
        assertThat(body).doesNotContain("Actividad de otro");
    }

    @Test
    void abiertaFalseDevuelveSoloLasCerradas() throws Exception
    {
        Actividad miActividad = guardarActividad("Actividad cerrada", yo);
        guardarVotacion(miActividad, false);

        Actividad otraAbierta = guardarActividad("Actividad abierta", yo);
        guardarVotacion(otraAbierta, true);

        MvcResult result = mockMvc.perform(get("/api/votaciones?abierta=false")
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Actividad cerrada");
        assertThat(body).doesNotContain("Actividad abierta");
    }

    @Test
    void incluyeVotacionesDeActividadesEnLasQueSoyParticipante() throws Exception
    {
        Actividad ajena = guardarActividad("Actividad a la que me sumo", otro);
        ajena.agregarParticipante(yo);
        actividadesRepository.save(ajena);
        guardarVotacion(ajena, true);

        MvcResult result = mockMvc.perform(get("/api/votaciones?abierta=true")
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Actividad a la que me sumo");
    }

    @Test
    void usuarioSinVotacionesDevuelveListaVacia() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/api/votaciones?abierta=true")
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).isEqualTo("[]");
    }

    @Test
    void pasarUsuarioIdComoQueryParamNoPermiteVerElDeOtroUsuario() throws Exception
    {
        Actividad ajena = guardarActividad("Actividad de otro", otro);
        guardarVotacion(ajena, true);

        MvcResult result = mockMvc.perform(get("/api/votaciones?abierta=true&usuarioId=" + otro.getId())
                                  .header("Authorization", "Bearer " + tokenYo))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Actividad de otro");
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
