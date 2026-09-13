package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
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
import com.tacs.backend.services.ServicioNotificaciones;
import com.tacs.backend.exceptions.AlternativaNotFoundException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.exceptions.VotacionNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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

  @Mock
  private ServicioNotificaciones servicioNotificaciones;

  private VotacionesServiceImplem service;

  private void inicializarService()
  {
    service = new VotacionesServiceImplem(votacionesRepository, actividadesRepository, usuarioRepository,
        votacionMapper, proveedorClima, servicioNotificaciones);
  }

  @Test
  void resolverConQuorumAlcanzadoReprogramaLaActividadALaFechaGanadora()
  {
    Actividad actividad = crearActividad(TipoEstadoActividad.PROPUESTA);
    LocalDateTime fechaGanadora = LocalDateTime.now().plusDays(3);
    Alternativa ganadora = crearAlternativa("1", 1, fechaGanadora);

    Votacion votacion = crearVotacion(actividad, 2, List.of(ganadora));
    votarDosVeces(votacion, ganadora);

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.resolverVotacion("10");

    assertThat(actividad.getFechaRealizacion()).isEqualTo(fechaGanadora);
    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.REPROGRAMADA);
    assertThat(votacion.isAbierta()).isFalse();
    assertThat(votacion.getAlternativaGanadora()).isEqualTo(ganadora);
    verify(actividadesRepository).save(actividad);
  }

  @Test
  void resolverSinQuorumCancelaLaActividadYNoDejaGanadora()
  {
    Actividad actividad = crearActividad(TipoEstadoActividad.PROPUESTA);
    Alternativa alternativa = crearAlternativa("1", 1, LocalDateTime.now().plusDays(3));

    Votacion votacion = crearVotacion(actividad, 5, List.of(alternativa)); // quorum 5, un solo voto
    votarDosVeces(votacion, alternativa); // 2 votos < 5 requeridos

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.resolverVotacion("10");

    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    assertThat(votacion.isAbierta()).isFalse();
    assertThat(votacion.getAlternativaGanadora()).isNull();
    verify(actividadesRepository).save(actividad);
  }

  @Test
  void resolverSinAlternativasCancelaLaActividad()
  {
    Actividad actividad = crearActividad(TipoEstadoActividad.PROPUESTA);
    Votacion votacion = crearVotacion(actividad, 1, List.of());

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.resolverVotacion("10");

    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    assertThat(votacion.getAlternativaGanadora()).isNull();
  }

  @Test
  void resolverSinQuorumYSinEstadoConfiguradoLanzaExcepcionClaraEnVezDeNullPointer()
  {
    Actividad actividad = crearActividad(null); // estado no configurado
    Alternativa alternativa = crearAlternativa("1", 1, LocalDateTime.now().plusDays(3));

    Votacion votacion = crearVotacion(actividad, 5, List.of(alternativa)); // quorum 5, un solo voto
    votacion.registrarVoto(votoDe("1", alternativa));

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.resolverVotacion("10"))
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

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.resolverVotacion("10"))
        .isInstanceOf(VotacionCerradaException.class);

    verify(actividadesRepository, never()).save(any());
    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void abreVotacionAutomaticaSoloConLosDiasQueTienenAlgunaHoraFavorableDentroDelRango()
  {
    LocalDateTime fechaOriginal = LocalDateTime.now().plusDays(1);
    Actividad actividad = crearActividad(TipoEstadoActividad.PROPUESTA, fechaOriginal);
    actividad.setId("50");
    actividad.setMinimoParticipantes(4);
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));
    actividad.setRangoReprogramacion(new RangoReprogramacion(3, 10, 14)); // 3 dias, franja 10-14hs (grilla: 10,12,14)

    Clima malo = new Clima(80, 20, 10);
    Clima bueno = new Clima(5, 22, 10);
    LocalDateTime fechaFavorable = fechaOriginal.plusDays(2).withHour(12).withMinute(0).withSecond(0).withNano(0);

    when(actividadesRepository.findById("50")).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId("50")).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(eq(UBICACION), any())).thenReturn(malo); // el resto de dias y horas
    when(proveedorClima.obtenerPronostico(eq(UBICACION), eq(fechaFavorable))).thenReturn(bueno); // salvo esta
    when(votacionesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(votacionMapper.votacionToVotacionDto(any())).thenReturn(mock(VotacionDto.class));

    inicializarService();
    Optional<VotacionDto> resultado = service.abrirVotacionAutomatica("50");

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
    Actividad actividad = crearActividad(TipoEstadoActividad.PROPUESTA, fechaOriginal);
    actividad.setId("54");
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20)); // max 30% de lluvia permitido
    actividad.setRangoReprogramacion(new RangoReprogramacion(1, 10, 14)); // 1 dia, grilla: 10, 12, 14

    LocalDateTime dia1 = fechaOriginal.plusDays(1);
    LocalDateTime hora10 = dia1.withHour(10).withMinute(0).withSecond(0).withNano(0);
    LocalDateTime hora12 = dia1.withHour(12).withMinute(0).withSecond(0).withNano(0);
    LocalDateTime hora14 = dia1.withHour(14).withMinute(0).withSecond(0).withNano(0);

    when(actividadesRepository.findById("54")).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId("54")).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(UBICACION, hora10)).thenReturn(new Clima(20, 22, 10)); // cumple, 20%
    when(proveedorClima.obtenerPronostico(UBICACION, hora12)).thenReturn(new Clima(80, 22, 10)); // no cumple
    when(proveedorClima.obtenerPronostico(UBICACION, hora14)).thenReturn(new Clima(5, 22, 10));  // cumple, 5%
    when(votacionesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(votacionMapper.votacionToVotacionDto(any())).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.abrirVotacionAutomatica("54");

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
    Actividad actividad = crearActividad(TipoEstadoActividad.PROPUESTA, fechaOriginal);
    actividad.setId("51");
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));
    actividad.setRangoReprogramacion(new RangoReprogramacion(3, 10, 14));

    when(actividadesRepository.findById("51")).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId("51")).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(any(), any())).thenReturn(new Clima(80, 20, 10)); // siempre desfavorable

    inicializarService();
    Optional<VotacionDto> resultado = service.abrirVotacionAutomatica("51");

    assertThat(resultado).isEmpty();
    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(actividadesRepository).save(actividad);
    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void cancelaLaActividadSiNoTieneRangoReprogramacionConfiguradoSinConsultarElClima()
  {
    Actividad actividad = crearActividad(TipoEstadoActividad.PROPUESTA);
    actividad.setId("55");
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));
    actividad.setRangoReprogramacion(null);

    when(actividadesRepository.findById("55")).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId("55")).thenReturn(Optional.empty());

    inicializarService();
    Optional<VotacionDto> resultado = service.abrirVotacionAutomatica("55");

    assertThat(resultado).isEmpty();
    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(proveedorClima, never()).obtenerPronostico(any(), any());
  }

  @Test
  void siLaFechaOriginalYaPasoUsaUnMargenMinimoParaLaFechaLimiteEnVezDeUnaFechaPasada()
  {
    LocalDateTime fechaOriginal = LocalDateTime.now().minusHours(2); // ya paso
    Actividad actividad = crearActividad(TipoEstadoActividad.PROPUESTA, fechaOriginal);
    actividad.setId("52");
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));
    actividad.setRangoReprogramacion(new RangoReprogramacion(3, 10, 14));

    when(actividadesRepository.findById("52")).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId("52")).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(any(), any())).thenReturn(new Clima(5, 22, 10)); // todos favorables
    when(votacionesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(votacionMapper.votacionToVotacionDto(any())).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.abrirVotacionAutomatica("52");

    ArgumentCaptor<Votacion> captor = ArgumentCaptor.forClass(Votacion.class);
    verify(votacionesRepository).save(captor.capture());
    LocalDateTime fechaLimite = captor.getValue().getFechaLimite();

    assertThat(fechaLimite).isAfter(LocalDateTime.now());
    assertThat(fechaLimite).isBefore(LocalDateTime.now().plusHours(2));
  }

  @Test
  void noAbreVotacionAutomaticaSiLaActividadYaTieneUnaAbierta()
  {
    Actividad actividad = crearActividad(TipoEstadoActividad.PROPUESTA);
    actividad.setId("53");
    actividad.setReglasClima(new ReglasClima(30, 10, 30, 20));

    when(actividadesRepository.findById("53")).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId("53")).thenReturn(Optional.of(new Votacion()));

    inicializarService();

    assertThatThrownBy(() -> service.abrirVotacionAutomatica("53"))
        .isInstanceOf(IllegalStateException.class);

    verify(proveedorClima, never()).obtenerPronostico(any(), any());
    verify(actividadesRepository, never()).save(any());
  }

  @Test
  void crearVotacionConQuorumMenorAlMinimoDeParticipantesLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null); // minimoParticipantes = 2, ver helper
    actividad.setId("60");

    VotacionPostDto dto = new VotacionPostDto(1, LocalDateTime.now().plusDays(1),
        List.of(new AlternativaPostDto(LocalDateTime.now().plusDays(2))));

    when(actividadesRepository.findById("60")).thenReturn(Optional.of(actividad));

    inicializarService();

    assertThatThrownBy(() -> service.crearVotacion("60", dto))
        .isInstanceOf(QuorumInvalidoException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void crearVotacionConQuorumIgualAlMinimoDeParticipantesEsValida()
  {
    Actividad actividad = crearActividad(null); // minimoParticipantes = 2
    actividad.setId("61");

    LocalDateTime fechaAlternativa = LocalDateTime.now().plusDays(2);
    VotacionPostDto dto = new VotacionPostDto(2, LocalDateTime.now(),
        List.of(new AlternativaPostDto(fechaAlternativa)));

    when(actividadesRepository.findById("61")).thenReturn(Optional.of(actividad));
    when(votacionesRepository.findByAbiertaTrueAndActividadId("61")).thenReturn(Optional.empty());
    when(proveedorClima.obtenerPronostico(UBICACION, fechaAlternativa)).thenReturn(new Clima(5, 22, 10));
    when(votacionesRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(votacionMapper.votacionToVotacionDto(any())).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.crearVotacion("61", dto); // no debe lanzar QuorumInvalidoException

    ArgumentCaptor<Votacion> captor = ArgumentCaptor.forClass(Votacion.class);
    verify(votacionesRepository).save(captor.capture());
    assertThat(captor.getValue().getQuorumMinimo()).isEqualTo(2);
  }

  @Test
  void votarRegistraElVotoYDevuelveLaVotacionActualizada()
  {
    Actividad actividad = crearActividad(null);
    Usuario participante = crearUsuarioConId("1");
    actividad.agregarParticipante(participante);

    Alternativa alternativa = crearAlternativa("1", 1, LocalDateTime.now().plusDays(2));
    Votacion votacion = crearVotacion(actividad, 2, List.of(alternativa));

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById("1")).thenReturn(Optional.of(participante));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.votar("10", "1", 1);

    assertThat(votacion.cantidadVotos(alternativa)).isEqualTo(1);
    verify(votacionesRepository).save(votacion);
  }

  @Test
  void votarConUsuarioInexistenteLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null);
    Alternativa alternativa = crearAlternativa("1", 1, LocalDateTime.now().plusDays(2));
    Votacion votacion = crearVotacion(actividad, 2, List.of(alternativa));

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById("99")).thenReturn(Optional.empty());

    inicializarService();

    assertThatThrownBy(() -> service.votar("10", "99", 1))
        .isInstanceOf(UsuarioNotFoundException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void votarConUsuarioQueNoEsParticipanteDeLaActividadLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null); // sin participantes agregados
    Usuario noParticipante = crearUsuarioConId("5");

    Alternativa alternativa = crearAlternativa("1", 1, LocalDateTime.now().plusDays(2));
    Votacion votacion = crearVotacion(actividad, 2, List.of(alternativa));

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById("5")).thenReturn(Optional.of(noParticipante));

    inicializarService();

    assertThatThrownBy(() -> service.votar("10", "5", 1))
        .isInstanceOf(IllegalStateException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void votarUnaAlternativaInexistenteLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null);
    Usuario participante = crearUsuarioConId("1");
    actividad.agregarParticipante(participante);

    Alternativa alternativa = crearAlternativa("1", 1, LocalDateTime.now().plusDays(2));
    Votacion votacion = crearVotacion(actividad, 2, List.of(alternativa)); // solo existe la alternativa numero 1

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById("1")).thenReturn(Optional.of(participante));

    inicializarService();

    assertThatThrownBy(() -> service.votar("10", "1", 99))
        .isInstanceOf(AlternativaNotFoundException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void votarSobreUnaVotacionCerradaLanzaExcepcion()
  {
    Actividad actividad = crearActividad(null);
    Votacion votacion = crearVotacion(actividad, 2, List.of());
    votacion.setAbierta(false);

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.votar("10", "1", 1))
        .isInstanceOf(VotacionCerradaException.class);

    verify(usuarioRepository, never()).findById(any());
    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void revotarPisaElVotoAnteriorEnVezDeAcumularlo()
  {
    Actividad actividad = crearActividad(null);
    Usuario participante = crearUsuarioConId("1");
    actividad.agregarParticipante(participante);

    Alternativa sabado = crearAlternativa("1", 1, LocalDateTime.now().plusDays(2));
    Alternativa domingo = crearAlternativa("2", 2, LocalDateTime.now().plusDays(3));
    Votacion votacion = crearVotacion(actividad, 2, List.of(sabado, domingo));

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(usuarioRepository.findById("1")).thenReturn(Optional.of(participante));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    service.votar("10", "1", 1); // vota sabado
    service.votar("10", "1", 2); // cambia de opinion, vota domingo

    assertThat(votacion.cantidadVotos(sabado)).isEqualTo(0);
    assertThat(votacion.cantidadVotos(domingo)).isEqualTo(1);
  }

  @Test
  void agregarAlternativaSobreVotacionCerradaLanzaExcepcion()
  {
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of());
    votacion.setAbierta(false);

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.agregarAlternativa("10", new AlternativaPostDto(LocalDateTime.now().plusDays(1))))
        .isInstanceOf(VotacionCerradaException.class);

    verify(votacionesRepository, never()).save(any());
    verify(proveedorClima, never()).obtenerPronostico(any(), any());
  }

  @Test
  void eliminarAlternativaSobreVotacionCerradaLanzaExcepcion()
  {
    Alternativa alternativa = crearAlternativa("1", 1, LocalDateTime.now().plusDays(1));
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of(alternativa));
    votacion.setAbierta(false);

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.eliminarAlternativa("10", 1))
        .isInstanceOf(VotacionCerradaException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void eliminarAlternativaInexistenteLanzaExcepcion()
  {
    Alternativa alternativa = crearAlternativa("1", 1, LocalDateTime.now().plusDays(1));
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of(alternativa));

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));

    inicializarService();

    assertThatThrownBy(() -> service.eliminarAlternativa("10", 99))
        .isInstanceOf(AlternativaNotFoundException.class);

    verify(votacionesRepository, never()).save(any());
  }

  @Test
  void eliminarAlternativaExistenteLaSacaDeLaVotacion()
  {
    Alternativa alternativa = crearAlternativa("1", 1, LocalDateTime.now().plusDays(1));
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of(alternativa));

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(votacionesRepository.save(votacion)).thenReturn(votacion);

    inicializarService();
    service.eliminarAlternativa("10", 1);

    assertThat(votacion.getAlternativas()).isEmpty();
  }

  @Test
  void obtenerVotacionInexistenteLanzaExcepcion()
  {
    when(votacionesRepository.findById("404")).thenReturn(Optional.empty());

    inicializarService();

    assertThatThrownBy(() -> service.obtenerVotacion("404"))
        .isInstanceOf(VotacionNotFoundException.class);
  }

  @Test
  void obtenerVotacionDevuelveElDtoMapeadoDeLaVotacionEncontrada()
  {
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of());
    VotacionDto dtoEsperado = mock(VotacionDto.class);

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));
    when(votacionMapper.votacionToVotacionDto(votacion)).thenReturn(dtoEsperado);

    inicializarService();
    VotacionDto resultado = service.obtenerVotacion("10");

    assertThat(resultado).isEqualTo(dtoEsperado);
  }

  @Test
  void eliminarVotacionInexistenteLanzaExcepcion()
  {
    when(votacionesRepository.findById("404")).thenReturn(Optional.empty());

    inicializarService();

    assertThatThrownBy(() -> service.eliminarVotacion("404"))
        .isInstanceOf(VotacionNotFoundException.class);

    verify(votacionesRepository, never()).delete(any());
  }

  @Test
  void eliminarVotacionExistenteLaBorraDelRepositorio()
  {
    Votacion votacion = crearVotacion(crearActividad(null), 2, List.of());

    when(votacionesRepository.findById("10")).thenReturn(Optional.of(votacion));

    inicializarService();
    service.eliminarVotacion("10");

    verify(votacionesRepository).delete(votacion);
  }

  @Test
  void votacionesConUsuarioInexistenteLanzaExcepcion()
  {
    when(usuarioRepository.existsById("404")).thenReturn(false);

    inicializarService();

    assertThatThrownBy(() -> service.votaciones("404", true))
        .isInstanceOf(UsuarioNotFoundException.class);

    verify(votacionesRepository, never()).findByAbiertaAndActividadOrganizadorId(anyBoolean(), any());
  }

  @Test
  void votacionesCombinaLasOrganizadasYLasParticipadas()
  {
    Votacion organizada = crearVotacion(crearActividad(null), 2, List.of());
    Votacion participada = crearVotacion(crearActividad(null), 3, List.of());

    when(usuarioRepository.existsById("1")).thenReturn(true);
    when(votacionesRepository.findByAbiertaYUsuarioInvolucrado(true, "1")).thenReturn(List.of(organizada, participada));
    when(votacionMapper.votacionToVotacionDto(organizada)).thenReturn(mock(VotacionDto.class));
    when(votacionMapper.votacionToVotacionDto(participada)).thenReturn(mock(VotacionDto.class));

    inicializarService();
    List<VotacionDto> resultado = service.votaciones("1", true);

    assertThat(resultado).hasSize(2);
  }

  @Test
  void votacionesDevuelveListaVaciaSiElUsuarioNoTieneNinguna()
  {
    when(usuarioRepository.existsById("1")).thenReturn(true);
    when(votacionesRepository.findByAbiertaYUsuarioInvolucrado(false, "1")).thenReturn(List.of());

    inicializarService();
    List<VotacionDto> resultado = service.votaciones("1", false);

    assertThat(resultado).isEmpty();
  }

  /* Auxiliares */
  private Actividad crearActividad(TipoEstadoActividad estado)
  {
    return crearActividad(estado, LocalDateTime.now().plusDays(1));
  }

  // PROPUESTA como origen porque, segun Estados, es el unico estado desde el
  // que se puede llegar tanto a REPROGRAMADA como a CANCELADA (los dos
  // destinos que usan los tests de resolverVotacion/abrirVotacionAutomatica).
  //
  // Organizador real (no null): el constructor de Actividad ya lo agrega
  // como participante automaticamente, y un null ahi se cuela en
  // getParticipantes() y rompe cualquier iteracion/stream sobre esa lista.
  private Actividad crearActividad(TipoEstadoActividad estado, LocalDateTime fecha)
  {
    Actividad actividad = new Actividad(
        "Asado en el parque",
        "Actividad de prueba",
        TipoActividad.AIRE_LIBRE,
        UBICACION,
        fecha,
        2,
        LocalDateTime.now(),
        2,
        10,
        crearUsuarioConId("999"));
    actividad.setEstado(estado);
    actividad.setRangoReprogramacion(new RangoReprogramacion(5, 0, 23));
    return actividad;
  }

  private Alternativa crearAlternativa(String id, int numero, LocalDateTime fecha)
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
    votacion.registrarVoto(votoDe("1", alternativa));
    votacion.registrarVoto(votoDe("2", alternativa));
  }

  private Voto votoDe(String usuarioId, Alternativa alternativa)
  {
    Usuario usuario = new Usuario("usuario" + usuarioId, "password", TipoRol.USER);
    usuario.setId(usuarioId);

    Voto voto = new Voto();
    voto.setUsuario(usuario);
    voto.setAlternativa(alternativa);
    return voto;
  }

  private Usuario crearUsuarioConId(String id)
  {
    Usuario usuario = new Usuario("usuario" + id, "password", TipoRol.USER);
    usuario.setId(id);
    return usuario;
  }
}
