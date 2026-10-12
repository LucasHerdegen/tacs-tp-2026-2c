package com.tacs.backend.controllers;

import com.tacs.backend.config.SecurityConfig;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.AuthService;
import com.tacs.backend.services.JwtService;
import com.tacs.backend.services.implem.JwtServiceImplem;
import com.tacs.backend.services.implem.telegrambot.TelegramVinculacionService;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UsuariosController.class)
@Import({SecurityConfig.class, JwtServiceImplem.class})
@TestPropertySource(properties = {
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes",
    "security.jwt.expiration-seconds=3600"
})
class UsuariosControllerTest
{
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private UsuarioRepository usuarioRepository;

  @MockitoBean
  private AuthService authService;

  @MockitoBean
  private TelegramVinculacionService telegramVinculacionService;

  @MockitoBean
  private CacheManager cacheManager;

  private Usuario admin;
  private Usuario normalUser;
  private String tokenAdmin;
  private String tokenUser;

  @BeforeEach
  void setUp()
  {
    admin = new Usuario("admin", "password-hash", TipoRol.ADMIN);
    admin.setId("admin-id");

    normalUser = new Usuario("usuario", "password-hash", TipoRol.USER);
    normalUser.setId("user-id");

    when(usuarioRepository.findById("admin-id")).thenReturn(Optional.of(admin));
    when(usuarioRepository.findById("user-id")).thenReturn(Optional.of(normalUser));

    tokenAdmin = jwtService.generarToken(admin);
    tokenUser = jwtService.generarToken(normalUser);
  }

  @Test
  void listarUsuariosSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(get("/api/usuarios"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listarUsuariosConRolUserDevuelveForbidden() throws Exception
  {
    mockMvc.perform(get("/api/usuarios")
            .header("Authorization", "Bearer " + tokenUser))
        .andExpect(status().isForbidden());
  }

  @Test
  void listarUsuariosConRolAdminDevuelveListaExitosa() throws Exception
  {
    List<UsuarioDto> lista = List.of(
        new UsuarioDto("admin-id", "admin", TipoRol.ADMIN, null),
        new UsuarioDto("user-id", "usuario", TipoRol.USER, null)
    );
    when(authService.listarUsuarios()).thenReturn(lista);

    mockMvc.perform(get("/api/usuarios")
            .header("Authorization", "Bearer " + tokenAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("admin"))
        .andExpect(jsonPath("$[0].rol").value("ADMIN"))
        .andExpect(jsonPath("$[1].username").value("usuario"))
        .andExpect(jsonPath("$[1].rol").value("USER"));
  }

  @Test
  void actualizarRolSinTokenDevuelveUnauthorized() throws Exception
  {
    String body = "{\"rol\":\"ADMIN\"}";

    mockMvc.perform(patch("/api/usuarios/user-id/rol")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void actualizarRolConRolUserDevuelveForbidden() throws Exception
  {
    String body = "{\"rol\":\"ADMIN\"}";

    mockMvc.perform(patch("/api/usuarios/user-id/rol")
            .header("Authorization", "Bearer " + tokenUser)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminActualizaRolDeOtroUsuarioExitosamente() throws Exception
  {
    String body = "{\"rol\":\"ADMIN\"}";
    UsuarioDto dtoActualizado = new UsuarioDto("user-id", "usuario", TipoRol.ADMIN, null);
    when(authService.actualizarRol("user-id", TipoRol.ADMIN, "admin-id")).thenReturn(dtoActualizado);

    mockMvc.perform(patch("/api/usuarios/user-id/rol")
            .header("Authorization", "Bearer " + tokenAdmin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("user-id"))
        .andExpect(jsonPath("$.rol").value("ADMIN"));
  }

  @Test
  void adminNoPuedeCambiarSuPropioRolDevuelveForbidden() throws Exception
  {
    String body = "{\"rol\":\"USER\"}";
    when(authService.actualizarRol("admin-id", TipoRol.USER, "admin-id"))
        .thenThrow(new AccesoDenegadoException("No podes cambiarte el rol a vos mismo"));

    mockMvc.perform(patch("/api/usuarios/admin-id/rol")
            .header("Authorization", "Bearer " + tokenAdmin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("No podes cambiarte el rol a vos mismo"));
  }

  @Test
  void obtenerMiUsuarioSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(get("/api/usuarios/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void obtenerMiUsuarioConTokenDevuelveDatos() throws Exception
  {
    UsuarioDto miDto = new UsuarioDto("user-id", "usuario", TipoRol.USER, null);
    when(authService.obtenerUsuario("user-id")).thenReturn(miDto);

    mockMvc.perform(get("/api/usuarios/me")
            .header("Authorization", "Bearer " + tokenUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("user-id"))
        .andExpect(jsonPath("$.username").value("usuario"));
  }
}
