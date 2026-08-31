package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.EstadoActividad;
import com.tacs.backend.domain.actividad.RangoReprogramacion;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.domain.votacion.Voto;
import com.tacs.backend.dtos.votacion.AlternativaPostDto;
import com.tacs.backend.dtos.votacion.VotacionDto;
import com.tacs.backend.dtos.votacion.VotacionPostDto;
import com.tacs.backend.exceptions.QuorumInvalidoException;
import com.tacs.backend.exceptions.VotacionCerradaException;
import com.tacs.backend.mappers.VotacionMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.exceptions.AlternativaNotFoundException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.VotacionNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VotacionesServiceImplemTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);

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

  private VotacionesServiceImplem service;

  private void inicializarService()
  {
    service = new VotacionesServiceImplem(votacionesRepository, actividadesRepository, usuarioRepository, votacionMapper, proveedorClima);
  }

  @Test
  void resolverConQuorumAlcanzadoReprogramaLaActividadALaFechaGanadora()
  {
    Actividad actividad = crearActividad(estadoConTransicionesA(TipoEstadoActividad.REPROGRAMADA));
    LocalDateTime fechaGanadora = LocalDateTime.now().plusDays(3);
    Alternativa ganadora = crearAlternativa(1L, 1, fechaGanadora);

    Votacion votacion = crearVotacion(actividad, 2, List.of(ganadora));
    votarDosVeces(votacion, ganadora);

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.resolverVotacion(10L);

    assertThat(actividad.getFechaRealizacion()).isEqualTo(fechaGanadora);
    assertThat(actividad.getEstado().getTipo()).isEqualTo(TipoEstadoActividad.REPROGRAMADA);
    assertThat(votacion.isAbierta()).isFalse();
    assertThat(votacion.getAlternativaGanadora()).isEqualTo(ganadora);
    verify(actividadesRepository).save(actividad);
  }

  @Test
  void resolverSinQuorumCancelaLaActividadYNoDejaGanadora()
  {
    Actividad actividad = crearActividad(estadoConTransicionesA(TipoEstadoActividad.CANCELADA));
    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(3));

    Votacion votacion = crearVotacion(actividad, 5, List.of(alternativa)); // quorum 5, un solo voto
    votarDosVeces(votacion, alternativa); // 2 votos < 5 requeridos

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.resolverVotacion(10L);

    assertThat(actividad.getEstado().getTipo()).isEqualTo(TipoEstadoActividad.CANCELADA);
    assertThat(votacion.isAbierta()).isFalse();
    assertThat(votacion.getAlternativaGanadora()).isNull();
    verify(actividadesRepository).save(actividad);
  }

  @Test
  void resolverSinAlternativasCancelaLaActividad()
  {
    Actividad actividad = crearActividad(estadoConTransicionesA(TipoEstadoActividad.CANCELADA));
    Votacion votacion = crearVotacion(actividad, 1, List.of());

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.resolverVotacion(10L);

    assertThat(actividad.getEstado().getTipo()).isEqualTo(TipoEstadoActividad.CANCELADA);
    assertThat(votacion.getAlternativaGanadora()).isNull();
  }

  @Test
  void resolverSinQuorumYSinEstadoConfiguradoLanzaExcepcionClaraEnVezDeNullPointer()
  {
    Actividad actividad = crearActividad(null); // estado no configurado
    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(3));

    Votacion votacion = crearVotacion(actividad, 5, List.of(alternativa)); // quorum 5, un solo voto
    votacion.registrarVoto(votoDe(1L, alternativa));

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.resolverVotacion(10L))
        .isInstanceOf(IllegalStateException.class);

    verify(actividadesRepository, never()).save(any());
    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void resolverUnaVotacionYaCerradaLanzaExcepcionYNoTocaLaActividad()
  {
    Actividad actividad = crearActividad(null);
    Votacion votacion = crearVotacion(actividad, 1, List.of());
    votacion.setAbierta(false);

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.resolverVotacion(10L))
        .isInstanceOf(VotacionCerradaException.class);

    verify(actividadesRepository, never()).save(any());
    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void abreVotacionAutomaticaSoloConLosDiasQueTienenAlgunaHoraFavorableDentroDelRango()
  {
    LocalDateTime fechaOriginal = LocalDateTime.now().plusDays(1);
    Actividad actividad = crearActividad(estadoConTransicionesA(TipoEstadoActividad.CANCELADA), fechaOriginal);
    actividad.setId(50L);
    actividad.setMinimoParticipantes(4);
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));
    actividad.setRangoReprogramacion(new RangoReprogramacion(3, 10, 14)); // 3 dias, franja 10-14hs (grilla: 10,12,14)

    Clima malo = new Clima(80, 20, 10);
    Clima bueno = new Clima(5, 22, 10);
    LocalDateTime fechaFavorable = fechaOriginal.plusDays(2).withHour(12).withMinute(0).withSecond(0).withNano(0);

    when(actividadesRepository.findById(50L)).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(50L)).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(eq(UBICACION), any())).thenReturn(malo); // el resto de dias y horas
    when(proveedorClima.obtenerPronostico(eq(UBICACION), eq(fechaFavorable))).thenReturn(bueno); // salvo esta
    when(votacionesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(votacionMapper.votacionToVotacionDto(any())).thenReturn(mock(VotacionDto.class));

    inicializarService();
    Optional<VotacionDto> resultado = service.abrirVotacionAutomatica(50L);

    assertThat(resultado).isPresent();

    ArgumentCaptor<Votacion> captor = ArgumentCaptor.forClass(Votacion.class);
    verify(votacionesRepository).save(captor.capture());
    Votacion votacionCreada = captor.getValue();

    assertThat(votacionCreada.getAlternativas()).hasSize(1);
    assertThat(votacionCreada.getAlternativas().get(0).getFecha()).isEqualTo(fechaFavorable);
    assertThat(votacionCreada.getQuorumMinimo()).isEqualTo(4); // = minimoParticipantes de la actividad
    assertThat(votacionCreada.isAbierta()).isTrue();
  }

  @Test
  void ofreceUnaAlternativaPorCadaHoraFavorableDelMismoDiaEnVezDeQuedarseConLaDeMenorProbabilidadDeLluvia()
  {
    LocalDateTime fechaOriginal = LocalDateTime.now().plusDays(1);
    Actividad actividad = crearActividad(estadoConTransicionesA(TipoEstadoActividad.CANCELADA), fechaOriginal);
    actividad.setId(54L);
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20)); // max 30% de lluvia permitido
    actividad.setRangoReprogramacion(new RangoReprogramacion(1, 10, 14)); // 1 dia, grilla: 10, 12, 14

    LocalDateTime dia1 = fechaOriginal.plusDays(1);
    LocalDateTime hora10 = dia1.withHour(10).withMinute(0).withSecond(0).withNano(0);
    LocalDateTime hora12 = dia1.withHour(12).withMinute(0).withSecond(0).withNano(0);
    LocalDateTime hora14 = dia1.withHour(14).withMinute(0).withSecond(0).withNano(0);

    when(actividadesRepository.findById(54L)).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(54L)).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(UBICACION, hora10)).thenReturn(new Clima(20, 22, 10)); // cumple, 20%
    when(proveedorClima.obtenerPronostico(UBICACION, hora12)).thenReturn(new Clima(80, 22, 10)); // no cumple
    when(proveedorClima.obtenerPronostico(UBICACION, hora14)).thenReturn(new Clima(5, 22, 10));  // cumple, 5%
    when(votacionesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(votacionMapper.votacionToVotacionDto(any())).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.abrirVotacionAutomatica(54L);

    ArgumentCaptor<Votacion> captor = ArgumentCaptor.forClass(Votacion.class);
    verify(votacionesRepository).save(captor.capture());
    List<Alternativa> alternativas = captor.getValue().getAlternativas();

    // hora10 y hora14 cumplen, se ofrecen ambas; hora12 no cumple y queda afuera
    assertThat(alternativas).hasSize(2);
    assertThat(alternativas).extracting(Alternativa::getFecha).containsExactly(hora10, hora14);
    assertThat(alternativas).extracting(Alternativa::getNumeroAltenativa).containsExactly(1, 2);
  }

  @Test
  void cancelaLaActividadSiNingunaHoraDeNingunDiaDelRangoTieneClimaFavorable()
  {
    LocalDateTime fechaOriginal = LocalDateTime.now().plusDays(1);
    Actividad actividad = crearActividad(estadoConTransicionesA(TipoEstadoActividad.CANCELADA), fechaOriginal);
    actividad.setId(51L);
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));
    actividad.setRangoReprogramacion(new RangoReprogramacion(3, 10, 14));

    when(actividadesRepository.findById(51L)).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(51L)).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(any(), any())).thenReturn(new Clima(80, 20, 10)); // siempre desfavorable

    inicializarService();
    Optional<VotacionDto> resultado = service.abrirVotacionAutomatica(51L);

    assertThat(resultado).isEmpty();
    assertThat(actividad.getEstado().getTipo()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(actividadesRepository).save(actividad);
    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void cancelaLaActividadSiNoTieneRangoReprogramacionConfiguradoSinConsultarElClima()
  {
    Actividad actividad = crearActividad(estadoConTransicionesA(TipoEstadoActividad.CANCELADA));
    actividad.setId(55L);
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));
    // sin actividad.setRangoReprogramacion(...): queda null

    when(actividadesRepository.findById(55L)).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(55L)).thenReturn(Optional.empty());

    inicializarService();
    Optional<VotacionDto> resultado = service.abrirVotacionAutomatica(55L);

    assertThat(resultado).isEmpty();
    assertThat(actividad.getEstado().getTipo()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(proveedorClima, never()).obtenerPronostico(any(), any());
  }

  @Test
  void siLaFechaOriginalYaPasoUsaUnMargenMinimoParaLaFechaLimiteEnVezDeUnaFechaPasada()
  {
    LocalDateTime fechaOriginal = LocalDateTime.now().minusHours(2); // ya paso
    Actividad actividad = crearActividad(estadoConTransicionesA(TipoEstadoActividad.CANCELADA), fechaOriginal);
    actividad.setId(52L);
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));
    actividad.setRangoReprogramacion(new RangoReprogramacion(3, 10, 14));

    when(actividadesRepository.findById(52L)).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(52L)).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(any(), any())).thenReturn(new Clima(5, 22, 10)); // todos favorables
    when(votacionesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(votacionMapper.votacionToVotacionDto(any())).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.abrirVotacionAutomatica(52L);

    ArgumentCaptor<Votacion> captor = ArgumentCaptor.forClass(Votacion.class);
    verify(votacionesRepository).save(captor.capture());
    LocalDateTime fechaLimite = captor.getValue().getFechaLimite();

    assertThat(fechaLimite).isAfter(LocalDateTime.now());
    assertThat(fechaLimite).isBefore(LocalDateTime.now().plusHours(2));
  }

  @Test
  void noAbreVotacionAutomaticaSiLaActividadYaTieneUnaAbierta()
  {
    Actividad actividad = crearActividad(estadoConTransicionesA(TipoEstadoActividad.CANCELADA));
    actividad.setId(53L);
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));

    when(actividadesRepository.findById(53L)).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(53L)).thenReturn(Optional.of(new Votacion()));

    inicializarService();

    assertThatThrownBy(() -> service.abrirVotacionAutomatica(53L))
        .isInstanceOf(IllegalStateException.class);

    verify(proveedorClima, never()).obtenerPronostico(any(), any());
    verify(actividadesRepository, never()).save(any());
  }

  @Test
  void crearVotacionConQuorumMenorAlMinimoDeParticipantesLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null); // minimoParticipantes = 2, ver helper
    actividad.setId(60L);

    VotacionPostDto dto = new VotacionPostDto(1, LocalDateTime.now().plusDays(1), List.of(new AlternativaPostDto(LocalDateTime.now().plusDays(2))));

    when(actividadesRepository.findById(60L)).thenReturn(Optional.of(actividad));

    inicializarService();

    assertThatThrownBy(() -> service.crearVotacion(60L, dto))
        .isInstanceOf(QuorumInvalidoException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void crearVotacionConQuorumIgualAlMinimoDeParticipantesEsValida()
  {
    Actividad actividad = crearActividad(null); // minimoParticipantes = 2
    actividad.setId(61L);

    LocalDateTime fechaAlternativa = LocalDateTime.now().plusDays(2);
    VotacionPostDto dto = new VotacionPostDto(2, LocalDateTime.now().plusDays(1), List.of(new AlternativaPostDto(fechaAlternativa)));

    when(actividadesRepository.findById(61L)).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId(61L)).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(UBICACION, fechaAlternativa)).thenReturn(new Clima(5, 22, 10));
    when(votacionesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(votacionMapper.votacionToVotacionDto(any())).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.crearVotacion(61L, dto); // no debe lanzar QuorumInvalidoException

    ArgumentCaptor<Votacion> captor = ArgumentCaptor.forClass(Votacion.class);
    verify(votacionesRepository).save(captor.capture());
    assertThat(captor.getValue().getQuorumMinimo()).isEqualTo(2);
  }

  @Test
  void votarRegistraElVotoYDevuelveLaVotacionActualizada()
  {
    Actividad actividad = crearActividad(null);
    Usuario participante = crearUsuarioConId(1L);
    actividad.agregarParticipante(participante);

    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(2));
    Votacion votacion = crearVotacion(actividad, 2, List.of(alternativa));

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(participante));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.votar(10L, 1L, 1);

    assertThat(votacion.cantidadVotos(alternativa)).isEqualTo(1);
    verify(votacionesRepository).save(votacion);
  }

  @Test
  void votarConUsuarioInexistenteLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null);
    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(2));
    Votacion votacion = crearVotacion(actividad, 2, List.of(alternativa));

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

    inicializarService();

    assertThatThrownBy(() -> service.votar(10L, 99L, 1))
        .isInstanceOf(UsuarioNotFoundException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void votarConUsuarioQueNoEsParticipanteDeLaActividadLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null); // sin participantes agregados
    Usuario noParticipante = crearUsuarioConId(5L);

    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(2));
    Votacion votacion = crearVotacion(actividad, 2, List.of(alternativa));

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById(5L)).thenReturn(Optional.of(noParticipante));

    inicializarService();

    assertThatThrownBy(() -> service.votar(10L, 5L, 1))
        .isInstanceOf(IllegalStateException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void votarUnaAlternativaInexistenteLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null);
    Usuario participante = crearUsuarioConId(1L);
    actividad.agregarParticipante(participante);

    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(2));
    Votacion votacion = crearVotacion(actividad, 2, List.of(alternativa)); // solo existe la alternativa numero 1

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(participante));

    inicializarService();

    assertThatThrownBy(() -> service.votar(10L, 1L, 99))
        .isInstanceOf(AlternativaNotFoundException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void votarSobreUnaVotacionCerradaLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null);
    Votacion votacion = crearVotacion(actividad, 2, List.of());
    votacion.setAbierta(false);

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.votar(10L, 1L, 1))
        .isInstanceOf(VotacionCerradaException.class);

    verify(usuarioRepository, never()).findById(any());
    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void revotarPisaElVotoAnteriorEnVezDeAcumularlo()
  {
    Actividad actividad = crearActividad(null);
    Usuario participante = crearUsuarioConId(1L);
    actividad.agregarParticipante(participante);

    Alternativa sabado = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(2));
    Alternativa domingo = crearAlternativa(2L, 2, LocalDateTime.now().plusDays(3));
    Votacion votacion = crearVotacion(actividad, 2, List.of(sabado, domingo));

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(participante));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.votar(10L, 1L, 1); // vota sabado
    service.votar(10L, 1L, 2); // cambia de opinion, vota domingo

    assertThat(votacion.cantidadVotos(sabado)).isEqualTo(0);
    assertThat(votacion.cantidadVotos(domingo)).isEqualTo(1);
  }

  @Test
  void agregarAlternativaSobreVotacionCerradaLanzaExcepcion()
  {
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of());
    votacion.setAbierta(false);

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.agregarAlternativa(10L, new AlternativaPostDto(LocalDateTime.now().plusDays(1))))
        .isInstanceOf(VotacionCerradaException.class);

    verify(votacionesRepository, never()).save(any());
    verify(proveedorClima, never()).obtenerPronostico(any(), any());
  }

  @Test
  void eliminarAlternativaSobreVotacionCerradaLanzaExcepcion()
  {
    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(1));
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of(alternativa));
    votacion.setAbierta(false);

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.eliminarAlternativa(10L, 1))
        .isInstanceOf(VotacionCerradaException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void eliminarAlternativaInexistenteLanzaExcepcion()
  {
    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(1));
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of(alternativa));

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.eliminarAlternativa(10L, 99))
        .isInstanceOf(AlternativaNotFoundException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void eliminarAlternativaExistenteLaSacaDeLaVotacion()
  {
    Alternativa alternativa = crearAlternativa(1L, 1, LocalDateTime.now().plusDays(1));
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of(alternativa));

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);

    inicializarService();
    service.eliminarAlternativa(10L, 1);

    assertThat(votacion.getAlternativas()).isEmpty();
  }

  @Test
  void obtenerVotacionInexistenteLanzaExcepcion()
  {
    when(votacionesRepository.findById(404L)).thenReturn(Optional.empty());

    inicializarService();

    assertThatThrownBy(() -> service.obtenerVotacion(404L))
        .isInstanceOf(VotacionNotFoundException.class);
  }

  @Test
  void obtenerVotacionDevuelveElDtoMapeadoDeLaVotacionEncontrada()
  {
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of());
    VotacionDto dtoEsperado = mock(VotacionDto.class);

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(dtoEsperado);

    inicializarService();
    VotacionDto resultado = service.obtenerVotacion(10L);

    assertThat(resultado).isEqualTo(dtoEsperado);
  }

  @Test
  void eliminarVotacionInexistenteLanzaExcepcion()
  {
    when(votacionesRepository.findById(404L)).thenReturn(Optional.empty());

    inicializarService();

    assertThatThrownBy(() -> service.eliminarVotacion(404L))
        .isInstanceOf(VotacionNotFoundException.class);

    verify(votacionesRepository, never()).delete(any());
  }

  @Test
  void eliminarVotacionExistenteLaBorraDelRepositorio()
  {
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of());

    when(votacionesRepository.findById(10L)).thenReturn(Optional.of(votacion));

    inicializarService();
    service.eliminarVotacion(10L);

    verify(votacionesRepository).delete(votacion);
  }

  @Test
  void votacionesConUsuarioInexistenteLanzaExcepcion()
  {
    when(usuarioRepository.existsById(404L)).thenReturn(false);

    inicializarService();

    assertThatThrownBy(() -> service.votaciones(404L, true))
        .isInstanceOf(UsuarioNotFoundException.class);

    verify(votacionesRepository, never()).findByAbiertaAndActividadOrganizadorId(anyBoolean(), any());
  }

  @Test
  void votacionesCombinaLasOrganizadasYLasParticipadas()
  {
    Votacion organizada = crearVotacion(crearActividad(null), 2, List.of());
    Votacion participada = crearVotacion(crearActividad(null), 3, List.of());

    when(usuarioRepository.existsById(1L)).thenReturn(true);
    when(votacionesRepository.findByAbiertaAndActividadOrganizadorId(true, 1L)).thenReturn(List.of(organizada));
    when(votacionesRepository.findByAbiertaAndActividadParticipantesId(true, 1L)).thenReturn(List.of(participada));
    when(votacionMapper.votacionToVotacionDto(organizada)).thenReturn(mock(VotacionDto.class));
    when(votacionMapper.votacionToVotacionDto(participada)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    List<VotacionDto> resultado = service.votaciones(1L, true);

    assertThat(resultado).hasSize(2);
  }

  @Test
  void votacionesDevuelveListaVaciaSiElUsuarioNoTieneNinguna()
  {
    when(usuarioRepository.existsById(1L)).thenReturn(true);
    when(votacionesRepository.findByAbiertaAndActividadOrganizadorId(false, 1L)).thenReturn(List.of());
    when(votacionesRepository.findByAbiertaAndActividadParticipantesId(false, 1L)).thenReturn(List.of());

    inicializarService();
    List<VotacionDto> resultado = service.votaciones(1L, false);

    assertThat(resultado).isEmpty();
  }

  /* Auxiliares */ 
  private Actividad crearActividad(EstadoActividad estado)
  {
    return crearActividad(estado, LocalDateTime.now().plusDays(1));
  }

  private Actividad crearActividad(EstadoActividad estado, LocalDateTime fecha)
  {
    Actividad actividad = new Actividad(
        "Asado en el parque",
        "Actividad de prueba",
        TipoActividad.AIRE_LIBRE,
        UBICACION,
        fecha,
        LocalDateTime.now(),
        2,
        10,
        null);
    actividad.setEstado(estado);
    return actividad;
  }

  private EstadoActividad estadoConTransicionesA(TipoEstadoActividad destino)
  {
    EstadoActividad destinoEstado = new EstadoActividad();
    destinoEstado.setTipo(destino);

    EstadoActividad origen = new EstadoActividad();
    origen.setTipo(TipoEstadoActividad.CONFIRMADA);
    origen.setPosiblesEstados(new ArrayList<>(List.of(destinoEstado)));

    return origen;
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
    Usuario usuario = new Usuario("usuario" + usuarioId, "password", TipoRol.USER);
    usuario.setId(usuarioId);

    Voto voto = new Voto();
    voto.setUsuario(usuario);
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