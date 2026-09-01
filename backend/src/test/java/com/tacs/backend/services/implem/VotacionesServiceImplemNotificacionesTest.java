package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.domain.votacion.Voto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.mappers.VotacionMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre las notificaciones de reprogramacion y cancelacion disparadas por la
 * resolucion de una votacion (el TODO "Notificar cuando se cancela" en
 * VotacionesServiceImplem.cancelarActividad).
 *
 * Va en una clase aparte de VotacionesServiceImplemTest para no chocar con
 * ediciones en paralelo sobre ese archivo; se puede fusionar despues.
 */
@ExtendWith(MockitoExtension.class)
class VotacionesServiceImplemNotificacionesTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);

  // Debe coincidir con la constante FORMATO de VotacionesServiceImplem. Si alla se
  // cambia el formato, este test hay que ajustarlo: es el precio de assertear el
  // contenido del mensaje en vez de solo verificar que se notifico.
  private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd:MM:yyyy HH:mm");

  @Mock
  private VotacionesRepository votacionesRepository;

  @Mock
  private ActividadesRepository actividadesRepository;

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private VotacionMapper votacionMapper;

  @Mock
  private ProveedorClima proveedorClima;

  @Mock
  private ServicioNotificaciones servicioNotificaciones;

  @InjectMocks
  private VotacionesServiceImplem service;

  /* ==================== Reprogramacion por quorum ==================== */

  @Test
  void conQuorumAlcanzadoNotificaLaReprogramacionConLaFechaViejaYLaNueva()
  {
    LocalDateTime fechaOriginal = LocalDateTime.now().plusDays(1);
    LocalDateTime fechaGanadora = LocalDateTime.now().plusDays(3);

    Actividad actividad = crearActividad(fechaOriginal);
    Alternativa ganadora = crearAlternativa(1L, 1, fechaGanadora);

    Votacion votacion = crearVotacion(actividad, 2, List.of(ganadora));
    votarDosVeces(votacion, ganadora);

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    service.resolverVotacion(10L);

    ArgumentCaptor<String> contenido = ArgumentCaptor.forClass(String.class);
    verify(servicioNotificaciones)
        .notificarATodos(contenido.capture(), eq(actividad.getParticipantes()));

    assertThat(contenido.getValue())
        .contains(actividad.getTitulo())
        .contains(fechaOriginal.format(FORMATO))
        .contains(fechaGanadora.format(FORMATO));
  }

  @Test
  void conQuorumAlcanzadoLaActividadQuedaEnLaFechaGanadora()
  {
    LocalDateTime fechaGanadora = LocalDateTime.now().plusDays(3);

    Actividad actividad = crearActividad(LocalDateTime.now().plusDays(1));
    Alternativa ganadora = crearAlternativa(1L, 1, fechaGanadora);

    Votacion votacion = crearVotacion(actividad, 2, List.of(ganadora));
    votarDosVeces(votacion, ganadora);

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    service.resolverVotacion(10L);

    assertThat(actividad.getFechaRealizacion()).isEqualTo(fechaGanadora);
    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.REPROGRAMADA);
    // Reprogramar resetea el flag para que se avise de la fecha nueva
    assertThat(actividad.isRecordatorioEnviado()).isFalse();
  }

  /* ==================== Cancelacion sin quorum ==================== */

  @Test
  void sinQuorumNotificaLaCancelacion()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusDays(1));
    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(3));

    // Quorum 5 con solo 2 votos: no se alcanza, la actividad se cancela
    Votacion votacion = crearVotacion(actividad, 5, List.of(alternativa));
    votarDosVeces(votacion, alternativa);

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    service.resolverVotacion(10L);

    verify(servicioNotificaciones)
        .notificarATodos(contains(actividad.getTitulo()), eq(actividad.getParticipantes()));

    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
  }

  /* ==================== Cancelacion sin alternativas favorables ==================== */

  // abrirVotacionAutomatica cancela la actividad cuando no encuentra ninguna
  // fecha con buen pronostico. Sin rangoReprogramacion configurado no hay donde
  // buscar, asi que buscarAlternativasFavorables devuelve vacio.
  @Test
  void sinAlternativasFavorablesNotificaLaCancelacion()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusDays(1));

    when(actividadesRepository.findById(1L)).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(1L)).thenReturn(Optional.empty());

    Optional<VotacionDto> resultado = service.abrirVotacionAutomatica(1L);

    assertThat(resultado).isEmpty();

    verify(servicioNotificaciones)
        .notificarATodos(contains(actividad.getTitulo()), eq(actividad.getParticipantes()));

    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
  }

  /* ==================== Auxiliares ==================== */

  // PROPUESTA porque, segun Estados, es el unico estado desde el que se puede
  // llegar tanto a REPROGRAMADA como a CANCELADA.
  //
  // Organizador real (no null): el constructor de Actividad ya lo agrega como
  // participante, y un null ahi se cuela en getParticipantes() y rompe
  // cualquier iteracion sobre esa lista.
  private Actividad crearActividad(LocalDateTime fechaRealizacion)
  {
    Actividad actividad = new Actividad(
        "Asado en el parque",
        "Actividad de prueba",
        TipoActividad.AIRE_LIBRE,
        UBICACION,
        fechaRealizacion,
        2,
        LocalDateTime.now(),
        2,
        10,
        crearUsuarioConId(999L));

    actividad.setEstado(TipoEstadoActividad.PROPUESTA);

    return actividad;
  }

  private Alternativa crearAlternativa(Long id, int numero, LocalDateTime fecha)
  {
    Alternativa alternativa = new Alternativa();
    alternativa.setId(id);
    alternativa.setNumeroAltenativa(numero);
    alternativa.setFecha(fecha);
    return alternativa;
  }

  private Votacion crearVotacion(Actividad actividad, int quorumMinimo, List<Alternativa> alternativas)
  {
    Votacion votacion = new Votacion();
    votacion.setActividad(actividad);
    votacion.setQuorumMinimo(quorumMinimo);
    votacion.setAbierta(true);
    alternativas.forEach(votacion::agregarAlternativa);
    return votacion;
  }

  private void votarDosVeces(Votacion votacion, Alternativa alternativa)
  {
    votacion.registrarVoto(votoDe(1L, alternativa));
    votacion.registrarVoto(votoDe(2L, alternativa));
  }

  private Voto votoDe(Long usuarioId, Alternativa alternativa)
  {
    Voto voto = new Voto();
    voto.setUsuario(crearUsuarioConId(usuarioId));
    voto.setAlternativa(alternativa);
    return voto;
  }

  private Usuario crearUsuarioConId(Long id)
  {
    Usuario usuario = new Usuario("usuario" + id, "password", TipoRol.USER);
    usuario.setId(id);
    return usuario;
  }
}
