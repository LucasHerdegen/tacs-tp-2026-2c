package com.tacs.backend.controllers;

import com.tacs.backend.config.SecurityConfig;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.admin.EstadisticasDto;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.EstadisticasService;
import com.tacs.backend.services.JwtService;
import com.tacs.backend.services.implem.JwtServiceImplem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EstadisticasController.class)
@Import({SecurityConfig.class, JwtServiceImplem.class})
@TestPropertySource(properties = {
    "security.jwt.secret=test-secret-key-with-at-least-32-bytes",
    "security.jwt.expiration-seconds=3600"
})
class EstadisticasControllerTest
{
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JwtService jwtService;

  @MockitoBean
  private UsuarioRepository usuarioRepository;

  @MockitoBean
  private EstadisticasService estadisticasService;

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
  void getEstadisticasSinTokenDevuelveUnauthorized() throws Exception
  {
    mockMvc.perform(get("/api/admin/estadisticas"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getEstadisticasConRolUserDevuelveForbidden() throws Exception
  {
    mockMvc.perform(get("/api/admin/estadisticas")
            .header("Authorization", "Bearer " + tokenUser))
        .andExpect(status().isForbidden());
  }

  @Test
  void getEstadisticasConRolAdminDevuelveEstadisticas() throws Exception
  {
    EstadisticasDto estadisticasDto = new EstadisticasDto(5L, 1L, 2L, 1L, 1L);
    when(estadisticasService.obtenerEstadisticas()).thenReturn(estadisticasDto);

    mockMvc.perform(get("/api/admin/estadisticas")
            .header("Authorization", "Bearer " + tokenAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actividadesCreadas").value(5))
        .andExpect(jsonPath("$.actividadesReprogramadas").value(1))
        .andExpect(jsonPath("$.actividadesCanceladas").value(2))
        .andExpect(jsonPath("$.actividadesConfirmadas").value(1))
        .andExpect(jsonPath("$.actividadesFinalizadas").value(1));
  }
}
