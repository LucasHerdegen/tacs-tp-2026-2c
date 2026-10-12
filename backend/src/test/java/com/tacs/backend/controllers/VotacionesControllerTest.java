package com.tacs.backend.controllers;

import com.tacs.backend.config.SecurityConfig;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.votacion.AlternativaPostDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.dtos.votacion.VotacionPostDto;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.JwtService;
import com.tacs.backend.services.VotacionesService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VotacionesController.class)
@Import({SecurityConfig.class, JwtServiceImplem.class})
@TestPropertySource(properties = {
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes",
    "security.jwt.expiration-seconds=3600"
})
class VotacionesControllerTest
{
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private UsuarioRepository usuarioRepository;

  @MockitoBean
  private VotacionesService votacionesService;

  @MockitoBean
  private CacheManager cacheManager;

  private Usuario organizador;
  private Usuario participante;
  private Usuario ajeno;
  private String tokenOrganizador;
  private String tokenParticipante;
  private String tokenAjeno;
  private VotacionDto mockVotacionDto;

  @BeforeEach
  void setUp()
  {
    organizador = new Usuario("organizador", "password-hash", TipoRol.USER);
    organizador.setId("org-id");

    participante = new Usuario("participante", "password-hash", TipoRol.USER);
    participante.setId("part-id");

    ajeno = new Usuario("ajeno", "password-hash", TipoRol.USER);
    ajeno.setId("ajeno-id");

    when(usuarioRepository.findById("org-id")).thenReturn(Optional.of(organizador));
    when(usuarioRepository.findById("part-id")).thenReturn(Optional.of(participante));
    when(usuarioRepository.findById("ajeno-id")).thenReturn(Optional.of(ajeno));

    tokenOrganizador = jwtService.generarToken(organizador);
    tokenParticipante = jwtService.generarToken(participante);
    tokenAjeno = jwtService.generarToken(ajeno);

    mockVotacionDto = new VotacionDto(
        "vot-1",
        LocalDateTime.now(),
        LocalDateTime.now().plusDays(2),
        null,
        null,
        List.of(),
        2,
        true,
        null
    );
  }

  // ==================== Tests de 401 Sin Token ====================

  @Test
  void getVotacionesSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(get("/api/votaciones"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void crearVotacionSinTokenDevuelveUnauthorized() throws Exception
  {
    String body = "{\"quorumMinimo\":2,\"fechaLimite\":\"2027-01-01T12:00:00\",\"alternativas\":[{\"fecha\":\"2027-01-01T10:00:00\"}]}";

    mockMvc.perform(post("/api/votaciones?actividadId=act-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getVotacionPorIdSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(get("/api/votaciones/vot-1"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void agregarAlternativaSinTokenDevuelveUnauthorized() throws Exception
  {
    String body = "{\"fecha\":\"2027-01-01T12:00:00\"}";

    mockMvc.perform(post("/api/votaciones/vot-1/alternativas")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void eliminarAlternativaSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(delete("/api/votaciones/vot-1/alternativas/1"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void votarSinTokenDevuelveUnauthorized() throws Exception
  {
    String body = "{\"numeroAlternativa\":1}";

    mockMvc.perform(post("/api/votaciones/vot-1/votos")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void cerrarVotacionSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(post("/api/votaciones/vot-1/cierre"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void eliminarVotacionSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(delete("/api/votaciones/vot-1"))
        .andExpect(status().isUnauthorized());
  }

  // ==================== Tests de Crear Votación ====================

  @Test
  void organizadorPuedeCrearVotacionDevuelveCreated() throws Exception
  {
    String body = "{\"quorumMinimo\":2,\"fechaLimite\":\"2027-01-01T12:00:00\",\"alternativas\":[{\"fecha\":\"2027-01-01T10:00:00\"}]}";
    when(votacionesService.crearVotacion(eq("act-1"), any(VotacionPostDto.class), eq("org-id")))
        .thenReturn(mockVotacionDto);

    mockMvc.perform(post("/api/votaciones?actividadId=act-1")
            .header("Authorization", "Bearer " + tokenOrganizador)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("vot-1"));
  }

  @Test
  void noOrganizadorNoPuedeCrearVotacionDevuelveForbidden() throws Exception
  {
    String body = "{\"quorumMinimo\":2,\"fechaLimite\":\"2027-01-01T12:00:00\",\"alternativas\":[{\"fecha\":\"2027-01-01T10:00:00\"}]}";
    when(votacionesService.crearVotacion(eq("act-1"), any(VotacionPostDto.class), eq("ajeno-id")))
        .thenThrow(new AccesoDenegadoException("Solo el organizador puede realizar esta acción"));

    mockMvc.perform(post("/api/votaciones?actividadId=act-1")
            .header("Authorization", "Bearer " + tokenAjeno)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Solo el organizador puede realizar esta acción"));
  }

  // ==================== Tests de Agregar Alternativa ====================

  @Test
  void organizadorPuedeAgregarAlternativaDevuelveOk() throws Exception
  {
    String body = "{\"fecha\":\"2027-01-01T12:00:00\"}";
    when(votacionesService.agregarAlternativa(eq("vot-1"), any(AlternativaPostDto.class), eq("org-id")))
        .thenReturn(mockVotacionDto);

    mockMvc.perform(post("/api/votaciones/vot-1/alternativas")
            .header("Authorization", "Bearer " + tokenOrganizador)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("vot-1"));
  }

  @Test
  void noOrganizadorNoPuedeAgregarAlternativaDevuelveForbidden() throws Exception
  {
    String body = "{\"fecha\":\"2027-01-01T12:00:00\"}";
    when(votacionesService.agregarAlternativa(eq("vot-1"), any(AlternativaPostDto.class), eq("part-id")))
        .thenThrow(new AccesoDenegadoException("Solo el organizador puede realizar esta acción"));

    mockMvc.perform(post("/api/votaciones/vot-1/alternativas")
            .header("Authorization", "Bearer " + tokenParticipante)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Solo el organizador puede realizar esta acción"));
  }

  // ==================== Tests de Eliminar Alternativa ====================

  @Test
  void organizadorPuedeEliminarAlternativaDevuelveNoContent() throws Exception
  {
    doNothing().when(votacionesService).eliminarAlternativa("vot-1", 1, "org-id");

    mockMvc.perform(delete("/api/votaciones/vot-1/alternativas/1")
            .header("Authorization", "Bearer " + tokenOrganizador))
        .andExpect(status().isNoContent());
  }

  @Test
  void noOrganizadorNoPuedeEliminarAlternativaDevuelveForbidden() throws Exception
  {
    doThrow(new AccesoDenegadoException("Solo el organizador puede realizar esta acción"))
        .when(votacionesService).eliminarAlternativa("vot-1", 1, "ajeno-id");

    mockMvc.perform(delete("/api/votaciones/vot-1/alternativas/1")
            .header("Authorization", "Bearer " + tokenAjeno))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Solo el organizador puede realizar esta acción"));
  }

  // ==================== Tests de Votar ====================

  @Test
  void participantePuedeRegistrarVotoDevuelveOk() throws Exception
  {
    String body = "{\"numeroAlternativa\":1}";
    when(votacionesService.votar("vot-1", "part-id", 1)).thenReturn(mockVotacionDto);

    mockMvc.perform(post("/api/votaciones/vot-1/votos")
            .header("Authorization", "Bearer " + tokenParticipante)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("vot-1"));
  }

  @Test
  void organizadorComoParticipantePuedeRegistrarVotoDevuelveOk() throws Exception
  {
    String body = "{\"numeroAlternativa\":1}";
    when(votacionesService.votar("vot-1", "org-id", 1)).thenReturn(mockVotacionDto);

    mockMvc.perform(post("/api/votaciones/vot-1/votos")
            .header("Authorization", "Bearer " + tokenOrganizador)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("vot-1"));
  }

  @Test
  void ajenoNoParticipanteNoPuedeRegistrarVotoDevuelveForbidden() throws Exception
  {
    String body = "{\"numeroAlternativa\":1}";
    when(votacionesService.votar("vot-1", "ajeno-id", 1))
        .thenThrow(new NoParticipanteException("Debes ser participante de la actividad para votar"));

    mockMvc.perform(post("/api/votaciones/vot-1/votos")
            .header("Authorization", "Bearer " + tokenAjeno)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Debes ser participante de la actividad para votar"));
  }

  // ==================== Tests de Cierre de Votación ====================

  @Test
  void organizadorPuedeCerrarVotacionDevuelveOk() throws Exception
  {
    when(votacionesService.resolverVotacion("vot-1", "org-id")).thenReturn(mockVotacionDto);

    mockMvc.perform(post("/api/votaciones/vot-1/cierre")
            .header("Authorization", "Bearer " + tokenOrganizador))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("vot-1"));
  }

  @Test
  void noOrganizadorNoPuedeCerrarVotacionDevuelveForbidden() throws Exception
  {
    when(votacionesService.resolverVotacion("vot-1", "part-id"))
        .thenThrow(new AccesoDenegadoException("Solo el organizador puede realizar esta acción"));

    mockMvc.perform(post("/api/votaciones/vot-1/cierre")
            .header("Authorization", "Bearer " + tokenParticipante))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Solo el organizador puede realizar esta acción"));
  }

  // ==================== Tests de Eliminar Votación ====================

  @Test
  void organizadorPuedeEliminarVotacionDevuelveNoContent() throws Exception
  {
    doNothing().when(votacionesService).eliminarVotacion("vot-1", "org-id");

    mockMvc.perform(delete("/api/votaciones/vot-1")
            .header("Authorization", "Bearer " + tokenOrganizador))
        .andExpect(status().isNoContent());
  }

  @Test
  void noOrganizadorNoPuedeEliminarVotacionDevuelveForbidden() throws Exception
  {
    doThrow(new AccesoDenegadoException("Solo el organizador puede realizar esta acción"))
        .when(votacionesService).eliminarVotacion("vot-1", "part-id");

    mockMvc.perform(delete("/api/votaciones/vot-1")
            .header("Authorization", "Bearer " + tokenParticipante))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Solo el organizador puede realizar esta acción"));
  }
}
