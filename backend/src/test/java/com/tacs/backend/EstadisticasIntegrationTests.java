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

import javax.sql.DataSource;
import java.sql.Connection;
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
class EstadisticasIntegrationTests
{
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ActividadesRepository actividadesRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

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

        admin = usuarioRepository.save(new Usuario("admin", passwordEncoder.encode("password-segura"), TipoRol.ADMIN));
        usuarioRepository.save(new Usuario("user", passwordEncoder.encode("password-segura"), TipoRol.USER));

        tokenAdmin = login("admin", "password-segura");
        tokenUser = login("user", "password-segura");
    }

    @Test
    void sinTokenDevuelveUnauthorized() throws Exception
    {
        mockMvc.perform(get("/api/admin/estadisticas"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioSinRolAdminDevuelveForbidden() throws Exception
    {
        mockMvc.perform(get("/api/admin/estadisticas")
               .header("Authorization", "Bearer " + tokenUser))
               .andExpect(status().isForbidden());
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

        MvcResult result = mockMvc.perform(get("/api/admin/estadisticas")
                                  .header("Authorization", "Bearer " + tokenAdmin))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"actividadesCreadas\":5");
        assertThat(body).contains("\"actividadesReprogramadas\":1");
        assertThat(body).contains("\"actividadesCanceladas\":1");
        assertThat(body).contains("\"actividadesConfirmadas\":1");
        assertThat(body).contains("\"actividadesFinalizadas\":1");
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
