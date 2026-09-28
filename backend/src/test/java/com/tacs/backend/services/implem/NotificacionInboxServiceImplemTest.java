package com.tacs.backend.services.implem;

import com.tacs.backend.domain.notificacion.Notificacion;
import com.tacs.backend.domain.notificacion.TipoNotificacion;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.notificaciones.NotificacionDto;
import com.tacs.backend.exceptions.NotificacionNotFoundException;
import com.tacs.backend.repositories.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionInboxServiceImplemTest
{
  @Mock
  private NotificacionRepository notificacionRepository;

  @InjectMocks
  private NotificacionInboxServiceImplem service;

  private Usuario usuario(String id)
  {
    Usuario usuario = new Usuario();
    usuario.setId(id);
    return usuario;
  }

  private Notificacion notificacion(String id, String usuarioId, boolean leida)
  {
    Notificacion notificacion = new Notificacion();
    notificacion.setId(id);
    notificacion.setUsuarioId(usuarioId);
    notificacion.setContenido("contenido");
    notificacion.setTipo(TipoNotificacion.ACTIVIDAD_CANCELADA);
    notificacion.setActividadId("act-1");
    notificacion.setLeida(leida);
    return notificacion;
  }

  @Test
  void crearPersisteUnaNotificacionNoLeidaParaElDestinatario()
  {
    ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
    when(notificacionRepository.save(any())).thenReturn(notificacion("n1", "usr-1", false));

    service.crear("contenido", TipoNotificacion.CLIMA_DESFAVORABLE, "act-1", null, usuario("usr-1"));

    verify(notificacionRepository).save(captor.capture());
    Notificacion guardada = captor.getValue();
    assertThat(guardada.getUsuarioId()).isEqualTo("usr-1");
    assertThat(guardada.getContenido()).isEqualTo("contenido");
    assertThat(guardada.getTipo()).isEqualTo(TipoNotificacion.CLIMA_DESFAVORABLE);
    assertThat(guardada.getActividadId()).isEqualTo("act-1");
    assertThat(guardada.isLeida()).isFalse();
    assertThat(guardada.getFechaCreacion()).isNotNull();
  }

  @Test
  void crearParaTodosLlamaCrearPorCadaDestinatario()
  {
    when(notificacionRepository.save(any())).thenReturn(notificacion("n1", "usr-1", false));

    service.crearParaTodos("contenido", TipoNotificacion.RECORDATORIO_INICIO, "act-1", null,
        List.of(usuario("usr-1"), usuario("usr-2")));

    verify(notificacionRepository, times(2)).save(any());
  }

  @Test
  void crearParaTodosNoCortaElLoteSiUnDestinatarioFalla()
  {
    when(notificacionRepository.save(any()))
        .thenThrow(new RuntimeException("fallo de mongo"))
        .thenReturn(notificacion("n2", "usr-2", false));

    service.crearParaTodos("contenido", TipoNotificacion.RECORDATORIO_INICIO, "act-1", null,
        List.of(usuario("usr-1"), usuario("usr-2")));

    verify(notificacionRepository, times(2)).save(any());
  }

  @Test
  void obtenerNotificacionesMapeaLaPaginaAEntidadesDto()
  {
    Pageable pageable = PageRequest.of(0, 20);
    Page<Notificacion> pagina = new PageImpl<>(List.of(notificacion("n1", "usr-1", false)));
    when(notificacionRepository.buscarPorUsuario("usr-1", null, pageable)).thenReturn(pagina);

    Page<NotificacionDto> resultado = service.obtenerNotificaciones("usr-1", null, pageable);

    assertThat(resultado.getContent()).hasSize(1);
    assertThat(resultado.getContent().get(0).id()).isEqualTo("n1");
  }

  @Test
  void contarNoLeidasDelegaEnElRepositorio()
  {
    when(notificacionRepository.contarNoLeidas("usr-1")).thenReturn(3L);

    assertThat(service.contarNoLeidas("usr-1")).isEqualTo(3L);
  }

  @Test
  void marcarComoLeidaActualizaLaNotificacionPropia()
  {
    Notificacion existente = notificacion("n1", "usr-1", false);
    when(notificacionRepository.findById("n1")).thenReturn(Optional.of(existente));
    when(notificacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.marcarComoLeida("n1", "usr-1");

    ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
    verify(notificacionRepository).save(captor.capture());
    assertThat(captor.getValue().isLeida()).isTrue();
    assertThat(captor.getValue().getFechaLectura()).isNotNull();
  }

  @Test
  void marcarComoLeidaDeOtroUsuarioLanzaNotFound()
  {
    when(notificacionRepository.findById("n1")).thenReturn(Optional.of(notificacion("n1", "usr-otro", false)));

    assertThatThrownBy(() -> service.marcarComoLeida("n1", "usr-1"))
        .isInstanceOf(NotificacionNotFoundException.class);

    verify(notificacionRepository, never()).save(any());
  }

  @Test
  void marcarComoLeidaInexistenteLanzaNotFound()
  {
    when(notificacionRepository.findById("n1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.marcarComoLeida("n1", "usr-1"))
        .isInstanceOf(NotificacionNotFoundException.class);
  }

  @Test
  void marcarTodasComoLeidasActualizaSoloLasNoLeidasDelUsuario()
  {
    when(notificacionRepository.findNoLeidasByUsuario("usr-1"))
        .thenReturn(List.of(notificacion("n1", "usr-1", false), notificacion("n2", "usr-1", false)));

    service.marcarTodasComoLeidas("usr-1");

    ArgumentCaptor<List<Notificacion>> captor = ArgumentCaptor.forClass(List.class);
    verify(notificacionRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).allMatch(Notificacion::isLeida);
  }

  @Test
  void marcarTodasComoLeidasSinPendientesNoFalla()
  {
    when(notificacionRepository.findNoLeidasByUsuario("usr-1")).thenReturn(List.of());

    service.marcarTodasComoLeidas("usr-1");

    verify(notificacionRepository).saveAll(eq(List.of()));
  }
}
