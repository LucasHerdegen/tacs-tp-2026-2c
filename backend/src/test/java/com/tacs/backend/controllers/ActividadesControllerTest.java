package com.tacs.backend.controllers;

import com.tacs.backend.config.SecurityConfig;
import com.tacs.backend.config.TimeConfig;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.JwtService;
import com.tacs.backend.services.implem.JwtServiceImplem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActividadesController.class)
@Import({SecurityConfig.class, JwtServiceImplem.class, TimeConfig.class})
@TestPropertySource(properties = {
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes",
    "security.jwt.expiration-seconds=3600"
})
class ActividadesControllerTest
{
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private UsuarioRepository usuarioRepository;

  @MockitoBean
  private ActividadesService actividadesService;

  @MockitoBean
  private CacheManager cacheManager;

  private Usuario yo;
  private String tokenYo;
  private ActividadDto mockActividadDto;

  @BeforeEach
  void setUp()
  {
    yo = new Usuario("yo", "password-hash", TipoRol.USER);
    yo.setId("user-id");

    when(usuarioRepository.findById("user-id")).thenReturn(Optional.of(yo));
    tokenYo = jwtService.generarToken(yo);

    UsuarioDto organizadorDto = new UsuarioDto("user-id", "yo", TipoRol.USER, null);
    mockActividadDto = new ActividadDto(
        "act-1",
        "Asado de fin de año",
        "Descripcion",
        TipoActividad.AIRE_LIBRE,
        null,
        LocalDateTime.now().plusDays(2),
        120,
        2,
        10,
        organizadorDto,
        List.of(organizadorDto),
        24,
        null,
        List.of(),
        TipoEstadoActividad.PROPUESTA,
        null
    );
  }

  @Test
  void getMisActividadesSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(get("/api/actividades/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getMisActividadesConOrganizadorTrueDevuelveOrganizadas() throws Exception
  {
    when(actividadesService.actividadesOrganizadas("user-id", null))
        .thenReturn(List.of(mockActividadDto));

    mockMvc.perform(get("/api/actividades/me?organizador=true")
            .header("Authorization", "Bearer " + tokenYo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].titulo").value("Asado de fin de año"));

    verify(actividadesService).actividadesOrganizadas("user-id", null);
  }

  @Test
  void getMisActividadesConOrganizadorFalseDevuelveParticipadas() throws Exception
  {
    when(actividadesService.actividadesParticipadas("user-id", null))
        .thenReturn(List.of(mockActividadDto));

    mockMvc.perform(get("/api/actividades/me?organizador=false")
            .header("Authorization", "Bearer " + tokenYo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].titulo").value("Asado de fin de año"));

    verify(actividadesService).actividadesParticipadas("user-id", null);
  }

  @Test
  void getMisActividadesSinFiltroOrganizadorDevuelveTodas() throws Exception
  {
    when(actividadesService.actividadesDelUsuario("user-id", null))
        .thenReturn(List.of(mockActividadDto));

    mockMvc.perform(get("/api/actividades/me")
            .header("Authorization", "Bearer " + tokenYo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].titulo").value("Asado de fin de año"));

    verify(actividadesService).actividadesDelUsuario("user-id", null);
  }

  @Test
  void getMisActividadesConFiltroEstado() throws Exception
  {
    when(actividadesService.actividadesOrganizadas("user-id", TipoEstadoActividad.PROPUESTA))
        .thenReturn(List.of(mockActividadDto));

    mockMvc.perform(get("/api/actividades/me?organizador=true&estado=PROPUESTA")
            .header("Authorization", "Bearer " + tokenYo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].titulo").value("Asado de fin de año"));

    verify(actividadesService).actividadesOrganizadas("user-id", TipoEstadoActividad.PROPUESTA);
  }

  @Test
  void getMisActividadesVaciaDevuelveListaVacia() throws Exception
  {
    when(actividadesService.actividadesDelUsuario("user-id", null))
        .thenReturn(List.of());

    mockMvc.perform(get("/api/actividades/me")
            .header("Authorization", "Bearer " + tokenYo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void obtenerActividadPorIdSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(get("/api/actividades/act-1"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void obtenerActividadPorIdConTokenDevuelveActividad() throws Exception
  {
    when(actividadesService.obtenerActividad("act-1")).thenReturn(mockActividadDto);

    mockMvc.perform(get("/api/actividades/act-1")
            .header("Authorization", "Bearer " + tokenYo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("act-1"))
        .andExpect(jsonPath("$.titulo").value("Asado de fin de año"));
  }

  @Test
  void unirseActividadConTokenDevuelveNoContent() throws Exception
  {
    mockMvc.perform(post("/api/actividades/act-1/participantes")
            .header("Authorization", "Bearer " + tokenYo))
        .andExpect(status().isNoContent());

    verify(actividadesService).unirseActividad("act-1", "user-id");
  }

  @Test
  void bajarseActividadConTokenDevuelveNoContent() throws Exception
  {
    mockMvc.perform(delete("/api/actividades/act-1/participantes")
            .header("Authorization", "Bearer " + tokenYo))
        .andExpect(status().isNoContent());

    verify(actividadesService).bajarseActividad("act-1", "user-id");
  }

  @Test
  void crearActividadParaDentroDeUnaHoraDevuelveCreated() throws Exception
  {
    when(actividadesService.createActividad(any(), any())).thenReturn(mockActividadDto);

    String body = """
        {
          "titulo": "Asado de fin de año",
          "descripcion": "Despedida del año",
          "tipoActividad": "AIRE_LIBRE",
          "ubicacion": {
            "direccion": "Av. Corrientes 1234",
            "latitud": -34.6037,
            "longitud": -58.3816
          },
          "fecha": "%s",
          "duracionEstimada": 120,
          "cantidadMinima": 2,
          "cantidadMaxima": 10
        }
        """.formatted(LocalDateTime.now().plusHours(1).toString());

    mockMvc.perform(post("/api/actividades")
            .header("Authorization", "Bearer " + tokenYo)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());
  }
}
