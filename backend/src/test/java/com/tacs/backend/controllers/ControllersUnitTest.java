package com.tacs.backend.controllers;

import com.tacs.backend.dtos.auth.LoginRequest;
import com.tacs.backend.dtos.auth.LoginResponse;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.AuthService;
import com.tacs.backend.services.EstadisticasService;
import com.tacs.backend.services.VotacionesService;
import com.tacs.backend.services.implem.telegrambot.TelegramVinculacionService;
import com.tacs.backend.domain.usuario.TipoRol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ControllersUnitTest {

    @Mock
    private ActividadesService actividadesService;

    @Mock
    private AuthService authService;

    @Mock
    private VotacionesService votacionesService;

    @Mock
    private EstadisticasService estadisticasService;

    @Mock
    private TelegramVinculacionService telegramVinculacionService;

    @InjectMocks
    private AuthController authController;

    @InjectMocks
    private UsuariosController usuariosController;

    @Test
    void testAuthLogin() {
        LoginResponse mockResponse = new LoginResponse("test-token", "Bearer", 3600L);
        when(authService.login(any(LoginRequest.class))).thenReturn(mockResponse);

        ResponseEntity<LoginResponse> response = authController.login(new LoginRequest("test", "test"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("test-token", response.getBody().token());
    }

    @Test
    void testGetUsuarios() {
        when(authService.listarUsuarios()).thenReturn(List.of(
                new UsuarioDto("1", "admin", TipoRol.ADMIN, null)
        ));

        ResponseEntity<List<UsuarioDto>> response = usuariosController.listarUsuarios();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("admin", response.getBody().get(0).username());
    }
}
