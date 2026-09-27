package com.tacs.backend.controllers;

import com.tacs.backend.dtos.notificaciones.ContadorNoLeidasDto;
import com.tacs.backend.dtos.notificaciones.NotificacionDto;
import com.tacs.backend.services.NotificacionInboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionesControllerTest
{
  @Mock
  private NotificacionInboxService notificacionInboxService;

  @Mock
  private Jwt jwt;

  @InjectMocks
  private NotificacionesController controller;

  @BeforeEach
  void setUp()
  {
    lenient().when(jwt.getClaim("id")).thenReturn("usr-1");
  }

  @Test
  void getMisNotificacionesDelegaEnElServiceConElUsuarioDelJwt()
  {
    Pageable pageable = PageRequest.of(0, 20);
    Page<NotificacionDto> pagina = new PageImpl<>(List.of());
    when(notificacionInboxService.obtenerNotificaciones("usr-1", null, pageable)).thenReturn(pagina);

    ResponseEntity<Page<NotificacionDto>> respuesta = controller.getMisNotificaciones(jwt, null, pageable);

    assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
    assertThat(respuesta.getBody()).isSameAs(pagina);
  }

  @Test
  void getCantidadNoLeidasDevuelveElContador()
  {
    when(notificacionInboxService.contarNoLeidas("usr-1")).thenReturn(3L);

    ResponseEntity<ContadorNoLeidasDto> respuesta = controller.getCantidadNoLeidas(jwt);

    assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
    assertThat(respuesta.getBody().cantidad()).isEqualTo(3L);
  }

  @Test
  void marcarComoLeidaDelegaEnElServiceYDevuelve204()
  {
    ResponseEntity<Void> respuesta = controller.marcarComoLeida("n1", jwt);

    verify(notificacionInboxService).marcarComoLeida("n1", "usr-1");
    assertThat(respuesta.getStatusCode().value()).isEqualTo(204);
  }

  @Test
  void marcarTodasComoLeidasDelegaEnElServiceYDevuelve204()
  {
    ResponseEntity<Void> respuesta = controller.marcarTodasComoLeidas(jwt);

    verify(notificacionInboxService).marcarTodasComoLeidas("usr-1");
    assertThat(respuesta.getStatusCode().value()).isEqualTo(204);
  }
}
