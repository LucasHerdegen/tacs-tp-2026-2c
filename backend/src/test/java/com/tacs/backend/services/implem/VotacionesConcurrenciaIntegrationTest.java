package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.mappers.ActividadesMapper;
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

@DataMongoTest(properties = {
    "spring.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/tacs_test}",
    "spring.data.mongodb.auto-index-creation=true"
})
@Import({
    VotacionesServiceImplem.class,
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
    OptimisticLockingRetry.class
})
class VotacionesConcurrenciaIntegrationTest
{
  @Autowired
  private VotacionesService votacionesService;

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
  @DisplayName("10 votos simultáneos dejan exactamente 10 votos registrados en MongoDB sin perder ninguno")
  void votar_diezVotosSimultaneos_dejaDiezVotosRegistrados() throws Exception
  {
    // Arrange
    int cantidadVotantes = 10;
    List<Usuario> votantes = new ArrayList<>();
    for (int i = 1; i <= cantidadVotantes; i++)
      votantes.add(usuarioRepository.save(new Usuario("user-" + i, "pass", TipoRol.USER)));

    Usuario organizador = usuarioRepository.save(new Usuario("organizador", "pass", TipoRol.USER));

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
}
