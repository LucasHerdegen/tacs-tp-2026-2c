package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.MedioContacto;
import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.exceptions.VotacionCerradaException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.mappers.ClimaMapper;
import com.tacs.backend.mappers.VotacionMapper;
import com.tacs.backend.persistence.mappers.ActividadMapper;
import com.tacs.backend.persistence.mappers.AlternativaMapper;
import com.tacs.backend.persistence.mappers.UsuarioMapper;
import com.tacs.backend.persistence.mappers.VotacionMapperEntity;
import com.tacs.backend.persistence.mappers.VotoMapper;
import com.tacs.backend.persistence.repositories.ActividadesMongoRepository;
import com.tacs.backend.persistence.repositories.ActividadesRepositoryImpl;
import com.tacs.backend.persistence.repositories.UsuarioMongoRepository;
import com.tacs.backend.persistence.repositories.UsuarioRepositoryImpl;
import com.tacs.backend.persistence.repositories.VotacionesMongoRepository;
import com.tacs.backend.persistence.repositories.VotacionesRepositoryImpl;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.NotificacionInboxService;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import com.tacs.backend.services.VotacionesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataMongoTest(properties = {
    "spring.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/tacs_test}",
    "spring.data.mongodb.auto-index-creation=true"
})
@Import({
    VotacionesServiceImplem.class,
    ActividadesServiceImplem.class,
    VotacionesRepositoryImpl.class,
    ActividadesRepositoryImpl.class,
    UsuarioRepositoryImpl.class,
    VotacionMapperEntity.class,
    ActividadMapper.class,
    AlternativaMapper.class,
    VotoMapper.class,
    UsuarioMapper.class,
    VotacionMapper.class,
    ActividadesMapper.class,
    ClimaMapper.class,
    OptimisticLockingRetry.class
})
class VotacionesConcurrenciaIntegrationTest
{
  @Autowired
  private VotacionesService votacionesService;

  @Autowired
  private ActividadesService actividadesService;

  @Autowired
  private VotacionesRepository votacionesRepository;

  @Autowired
  private ActividadesRepository actividadesRepository;

  @Autowired
  private UsuarioRepository usuarioRepository;

  @Autowired
  private VotacionesMongoRepository votacionesMongoRepository;

  @Autowired
  private ActividadesMongoRepository actividadesMongoRepository;

  @Autowired
  private UsuarioMongoRepository usuarioMongoRepository;

  @MockitoBean
  private ProveedorClima proveedorClima;

  @MockitoBean
  private ServicioNotificaciones servicioNotificaciones;

  @MockitoBean
  private NotificacionInboxService notificacionInboxService;

  @MockitoBean
  private CacheManager cacheManager;

  @BeforeEach
  void setUp()
  {
    votacionesMongoRepository.deleteAll();
    actividadesMongoRepository.deleteAll();
    usuarioMongoRepository.deleteAll();
  }

  @AfterEach
  void tearDown()
  {
    votacionesMongoRepository.deleteAll();
    actividadesMongoRepository.deleteAll();
    usuarioMongoRepository.deleteAll();
  }

  @Test
  @DisplayName("10 votos simultáneos de usuarios de Telegram en múltiples votaciones se registran en MongoDB sin lanzar DuplicateKeyException (500)")
  void votar_diezVotosSimultaneosDeUsuariosTelegram_dejaDiezVotosRegistradosSinError500() throws Exception
  {
    // Arrange
    int cantidadVotantes = 10;
    List<Usuario> votantes = new ArrayList<>();
    for (int i = 1; i <= cantidadVotantes; i++)
    {
      Usuario u = new Usuario("tg_" + i, "pass", TipoRol.USER);
      u.setMedioContacto(new MedioContacto("chat-" + i, TipoMedioContacto.TELEGRAM));
      votantes.add(usuarioRepository.save(u));
    }

    Usuario organizador = new Usuario("organizador", "pass", TipoRol.USER);
    organizador.setMedioContacto(new MedioContacto("chat-org", TipoMedioContacto.TELEGRAM));
    organizador = usuarioRepository.save(organizador);

    // Primera votacion donde ya votaron usuarios de Telegram (para verificar que no colisione ningun indice unico en votaciones)
    Actividad actividadPrevia = new Actividad(
        "Previa", "partido previo", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Palermo", -34.58, -58.43),
        LocalDateTime.now().plusDays(1), 2, LocalDateTime.now(), 2, 20, organizador
    );
    actividadPrevia.cambiarEstado(TipoEstadoActividad.CONFIRMADA);
    for (Usuario votante : votantes)
      actividadPrevia.agregarParticipante(votante);
    actividadPrevia = actividadesRepository.save(actividadPrevia);

    Alternativa altPrevia = new Alternativa();
    altPrevia.setId("alt-previa");
    altPrevia.setNumeroAlternativa(1);
    altPrevia.setFecha(LocalDateTime.now().plusDays(2));

    Votacion votacionPrevia = new Votacion();
    votacionPrevia.setAbierta(true);
    votacionPrevia.setActividad(actividadPrevia);
    votacionPrevia.agregarAlternativa(altPrevia);
    votacionPrevia.setQuorumMinimo(2);
    votacionPrevia = votacionesRepository.save(votacionPrevia);
    votacionesService.votar(votacionPrevia.getId(), votantes.getFirst().getId(), 1);

    // Segunda votacion donde los 10 usuarios de Telegram votan concurrentemente
    Actividad actividad = new Actividad(
        "Fútbol", "picado", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Palermo", -34.58, -58.43),
        LocalDateTime.now().plusDays(2), 2, LocalDateTime.now(), 2, 20, organizador
    );
    actividad.cambiarEstado(TipoEstadoActividad.CONFIRMADA);
    for (Usuario votante : votantes)
      actividad.agregarParticipante(votante);
    actividad = actividadesRepository.save(actividad);

    Alternativa alt1 = new Alternativa();
    alt1.setId("alt-1");
    alt1.setNumeroAlternativa(1);
    alt1.setFecha(LocalDateTime.now().plusDays(3));

    Alternativa alt2 = new Alternativa();
    alt2.setId("alt-2");
    alt2.setNumeroAlternativa(2);
    alt2.setFecha(LocalDateTime.now().plusDays(4));

    Votacion votacion = new Votacion();
    votacion.setAbierta(true);
    votacion.setActividad(actividad);
    votacion.agregarAlternativa(alt1);
    votacion.agregarAlternativa(alt2);
    votacion.setQuorumMinimo(2);
    votacion = votacionesRepository.save(votacion);
    String votacionId = votacion.getId();

    // Act: 10 hilos votan al mismo instante contra MongoDB real
    ExecutorService executor = Executors.newFixedThreadPool(cantidadVotantes);
    CountDownLatch readyLatch = new CountDownLatch(cantidadVotantes);
    CountDownLatch startLatch = new CountDownLatch(1);

    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (int i = 0; i < cantidadVotantes; i++)
    {
      final String usuarioId = votantes.get(i).getId();
      final int alternativaElegida = (i % 2) + 1;

      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        readyLatch.countDown();
        try
        {
          startLatch.await();
          votacionesService.votar(votacionId, usuarioId, alternativaElegida);
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }, executor);

      futures.add(future);
    }

    readyLatch.await(5, TimeUnit.SECONDS);
    startLatch.countDown();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(20, TimeUnit.SECONDS);
    executor.shutdown();

    // Assert: los 10 votos estan registrados en MongoDB
    Votacion resultadoFinal = votacionesRepository.findById(votacionId).orElseThrow();
    assertThat(resultadoFinal.getVotos()).hasSize(10);

    for (Usuario votante : votantes)
    {
      boolean votoRegistrado = resultadoFinal.getVotos().stream()
          .anyMatch(v -> v.getUsuario().getId().equals(votante.getId()));
      assertThat(votoRegistrado)
          .as("El voto de " + votante.getId() + " debe estar registrado")
          .isTrue();
    }
  }

  @Test
  @DisplayName("Votos simultáneos con usuarios ajenos rechazan con NoParticipanteException sin perder votos válidos")
  void votar_votosSimultaneosConUsuariosNoParticipantes_registraParticipantesYRechazaAjenos() throws Exception
  {
    // Arrange: 6 participantes y 4 usuarios no participantes intentan votar simultaneamente
    List<Usuario> participantes = new ArrayList<>();
    for (int i = 1; i <= 6; i++)
      participantes.add(usuarioRepository.save(new Usuario("participante-" + i, "pass", TipoRol.USER)));

    List<Usuario> ajenos = new ArrayList<>();
    for (int i = 1; i <= 4; i++)
      ajenos.add(usuarioRepository.save(new Usuario("ajeno-" + i, "pass", TipoRol.USER)));

    Usuario organizador = usuarioRepository.save(new Usuario("organizador", "pass", TipoRol.USER));

    Actividad actividad = new Actividad(
        "Asado", "Encuentro", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Caballito", -34.61, -58.44),
        LocalDateTime.now().plusDays(2), 3, LocalDateTime.now(), 2, 20, organizador
    );
    actividad.cambiarEstado(TipoEstadoActividad.CONFIRMADA);
    for (Usuario p : participantes)
      actividad.agregarParticipante(p);
    actividad = actividadesRepository.save(actividad);

    Alternativa alt1 = new Alternativa();
    alt1.setId("alt-1");
    alt1.setNumeroAlternativa(1);
    alt1.setFecha(LocalDateTime.now().plusDays(3));

    Votacion votacion = new Votacion();
    votacion.setAbierta(true);
    votacion.setActividad(actividad);
    votacion.agregarAlternativa(alt1);
    votacion.setQuorumMinimo(2);
    votacion = votacionesRepository.save(votacion);
    String votacionId = votacion.getId();

    List<Usuario> todosLosIntentos = new ArrayList<>();
    todosLosIntentos.addAll(participantes);
    todosLosIntentos.addAll(ajenos);

    int totalHilos = todosLosIntentos.size();
    ExecutorService executor = Executors.newFixedThreadPool(totalHilos);
    CountDownLatch readyLatch = new CountDownLatch(totalHilos);
    CountDownLatch startLatch = new CountDownLatch(1);

    AtomicInteger exitos = new AtomicInteger(0);
    AtomicInteger rechazadosNoParticipante = new AtomicInteger(0);
    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (Usuario u : todosLosIntentos)
    {
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        readyLatch.countDown();
        try
        {
          startLatch.await();
          votacionesService.votar(votacionId, u.getId(), 1);
          exitos.incrementAndGet();
        }
        catch (NoParticipanteException e)
        {
          rechazadosNoParticipante.incrementAndGet();
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }, executor);

      futures.add(future);
    }

    readyLatch.await(5, TimeUnit.SECONDS);
    startLatch.countDown();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(20, TimeUnit.SECONDS);
    executor.shutdown();

    // Assert
    assertThat(exitos.get()).isEqualTo(6);
    assertThat(rechazadosNoParticipante.get()).isEqualTo(4);

    Votacion resultadoFinal = votacionesRepository.findById(votacionId).orElseThrow();
    assertThat(resultadoFinal.getVotos()).hasSize(6);
  }

  @Test
  @DisplayName("Cerrar votación mientras otros votan cierra sin 409 para el organizador, reprograma una sola vez y notifica una sola vez")
  void resolverVotacion_mientrasOtrosVotan_cierraReprogramaYNotificaUnaSolaVez() throws Exception
  {
    Usuario organizador = usuarioRepository.save(new Usuario("organizador", "pass", TipoRol.USER));
    List<Usuario> participantes = new ArrayList<>();
    for (int i = 1; i <= 6; i++)
      participantes.add(usuarioRepository.save(new Usuario("votante-" + i, "pass", TipoRol.USER)));

    Actividad actividad = new Actividad(
        "Tenis", "Dobles", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Belgrano", -34.56, -58.45),
        LocalDateTime.now().plusDays(2), 2, LocalDateTime.now(), 2, 20, organizador
    );
    actividad.cambiarEstado(TipoEstadoActividad.CONFIRMADA);
    for (Usuario p : participantes)
      actividad.agregarParticipante(p);
    actividad = actividadesRepository.save(actividad);
    String actividadId = actividad.getId();

    Alternativa alt1 = new Alternativa();
    alt1.setId("alt-1");
    alt1.setNumeroAlternativa(1);
    alt1.setFecha(LocalDateTime.now().plusDays(3).withNano(0));

    Votacion votacion = new Votacion();
    votacion.setAbierta(true);
    votacion.setActividad(actividad);
    votacion.agregarAlternativa(alt1);
    votacion.setQuorumMinimo(2);
    votacion = votacionesRepository.save(votacion);
    String votacionId = votacion.getId();

    // Dos votos iniciales para asegurar quorum antes del cierre concurrente
    votacionesService.votar(votacionId, participantes.get(0).getId(), 1);
    votacionesService.votar(votacionId, participantes.get(1).getId(), 1);

    int hilosVotantesConcurrentes = 4;
    int totalHilos = hilosVotantesConcurrentes + 1;
    ExecutorService executor = Executors.newFixedThreadPool(totalHilos);
    CountDownLatch readyLatch = new CountDownLatch(totalHilos);
    CountDownLatch startLatch = new CountDownLatch(1);

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    final String orgId = organizador.getId();

    // Hilo del organizador cerrando la votacion
    futures.add(CompletableFuture.runAsync(() -> {
      readyLatch.countDown();
      try
      {
        startLatch.await();
        votacionesService.resolverVotacion(votacionId, orgId);
      }
      catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }, executor));

    // Hilos de participantes votando simultaneamente
    for (int i = 2; i < 6; i++)
    {
      final String uid = participantes.get(i).getId();
      futures.add(CompletableFuture.runAsync(() -> {
        readyLatch.countDown();
        try
        {
          startLatch.await();
          votacionesService.votar(votacionId, uid, 1);
        }
        catch (VotacionCerradaException ignored)
        {
          // Esperado si el voto llega despues del cierre condicional
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }, executor));
    }

    readyLatch.await(5, TimeUnit.SECONDS);
    startLatch.countDown();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(20, TimeUnit.SECONDS);
    executor.shutdown();

    Votacion votacionFinal = votacionesRepository.findById(votacionId).orElseThrow();
    Actividad actividadFinal = actividadesRepository.findById(actividadId).orElseThrow();

    assertThat(votacionFinal.isAbierta()).isFalse();
    assertThat(actividadFinal.getEstado()).isEqualTo(TipoEstadoActividad.REPROGRAMADA);
    assertThat(actividadFinal.getCambiosFecha()).hasSize(1);
    verify(servicioNotificaciones, times(1)).notificarATodos(any(), any());
  }

  @Test
  @DisplayName("Cancelar actividad mientras otros votan cierra la votación, cancela la actividad y notifica una sola vez")
  void cancelarActividad_mientrasOtrosVotan_cierraVotacionCancelaYNotifica() throws Exception
  {
    Usuario organizador = usuarioRepository.save(new Usuario("organizador", "pass", TipoRol.USER));
    List<Usuario> participantes = new ArrayList<>();
    for (int i = 1; i <= 5; i++)
      participantes.add(usuarioRepository.save(new Usuario("votante-" + i, "pass", TipoRol.USER)));

    Actividad actividad = new Actividad(
        "Ciclismo", "Salida grupal", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Recoleta", -34.58, -58.39),
        LocalDateTime.now().plusDays(2), 2, LocalDateTime.now(), 2, 20, organizador
    );
    actividad.cambiarEstado(TipoEstadoActividad.CONFIRMADA);
    for (Usuario p : participantes)
      actividad.agregarParticipante(p);
    actividad = actividadesRepository.save(actividad);
    String actividadId = actividad.getId();

    Alternativa alt1 = new Alternativa();
    alt1.setId("alt-1");
    alt1.setNumeroAlternativa(1);
    alt1.setFecha(LocalDateTime.now().plusDays(3).withNano(0));

    Votacion votacion = new Votacion();
    votacion.setAbierta(true);
    votacion.setActividad(actividad);
    votacion.agregarAlternativa(alt1);
    votacion.setQuorumMinimo(2);
    votacion = votacionesRepository.save(votacion);
    String votacionId = votacion.getId();

    int totalHilos = participantes.size() + 1;
    ExecutorService executor = Executors.newFixedThreadPool(totalHilos);
    CountDownLatch readyLatch = new CountDownLatch(totalHilos);
    CountDownLatch startLatch = new CountDownLatch(1);

    List<CompletableFuture<Void>> futures = new ArrayList<>();
    final String orgId = organizador.getId();

    // Hilo del organizador cancelando la actividad mientras los demas votan
    futures.add(CompletableFuture.runAsync(() -> {
      readyLatch.countDown();
      try
      {
        startLatch.await();
        actividadesService.cambiarEstado(actividadId, orgId, TipoEstadoActividad.CANCELADA);
      }
      catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }, executor));

    for (Usuario p : participantes)
    {
      final String uid = p.getId();
      futures.add(CompletableFuture.runAsync(() -> {
        readyLatch.countDown();
        try
        {
          startLatch.await();
          votacionesService.votar(votacionId, uid, 1);
        }
        catch (VotacionCerradaException ignored)
        {
          // Esperado si el voto ocurre tras el cierre condicional de la votacion
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }, executor));
    }

    readyLatch.await(5, TimeUnit.SECONDS);
    startLatch.countDown();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(20, TimeUnit.SECONDS);
    executor.shutdown();

    Votacion votacionFinal = votacionesRepository.findById(votacionId).orElseThrow();
    Actividad actividadFinal = actividadesRepository.findById(actividadId).orElseThrow();

    assertThat(votacionFinal.isAbierta()).isFalse();
    assertThat(actividadFinal.getEstado()).isEqualTo(TipoEstadoActividad.CANCELADA);
    verify(servicioNotificaciones, times(1)).notificarATodos(any(), any());
  }
}
