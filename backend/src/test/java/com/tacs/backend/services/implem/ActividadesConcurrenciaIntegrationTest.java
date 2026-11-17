package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.exceptions.CapacidadMaximaException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.mappers.ClimaMapper;
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
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.NotificacionInboxService;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
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
    ActividadesServiceImplem.class,
    ActividadesRepositoryImpl.class,
    UsuarioRepositoryImpl.class,
    VotacionesRepositoryImpl.class,
    ActividadMapper.class,
    UsuarioMapper.class,
    VotacionMapperEntity.class,
    AlternativaMapper.class,
    VotoMapper.class,
    ActividadesMapper.class,
    ClimaMapper.class,
    OptimisticLockingRetry.class
})
class ActividadesConcurrenciaIntegrationTest
{
  @Autowired
  private ActividadesService actividadesService;

  @Autowired
  private ActividadesRepository actividadesRepository;

  @Autowired
  private UsuarioRepository usuarioRepository;

  @Autowired
  private ActividadesMongoRepository actividadesMongoRepository;

  @Autowired
  private UsuarioMongoRepository usuarioMongoRepository;

  @Autowired
  private VotacionesMongoRepository votacionesMongoRepository;

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
  @DisplayName("20 altas simultáneas con 28 lugares libres anotan exitosamente a los 20 en MongoDB")
  void unirseActividad_veinteAltasSimultaneasConLugaresLibres_anotaALosVeinte() throws Exception
  {
    // Arrange: actividad con organizador (1 ocupado) y maximo 29 (28 lugares libres)
    Usuario organizador = usuarioRepository.save(new Usuario("organizador", "pass", TipoRol.USER));

    Actividad actividad = new Actividad(
        "Fútbol 5", "Picado de los viernes", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Palermo", -34.58, -58.43),
        LocalDateTime.now().plusDays(2), 2, LocalDateTime.now(), 2, 29, organizador
    );
    actividad.cambiarEstado(TipoEstadoActividad.PROPUESTA);
    actividad = actividadesRepository.save(actividad);
    String actividadId = actividad.getId();

    int cantidadUsuarios = 20;
    List<Usuario> usuarios = new ArrayList<>();
    for (int i = 1; i <= cantidadUsuarios; i++)
      usuarios.add(usuarioRepository.save(new Usuario("user-" + i, "pass", TipoRol.USER)));

    // Act: 20 hilos intentan unirse exactamente al mismo tiempo contra MongoDB real
    ExecutorService executor = Executors.newFixedThreadPool(cantidadUsuarios);
    CountDownLatch readyLatch = new CountDownLatch(cantidadUsuarios);
    CountDownLatch startLatch = new CountDownLatch(1);

    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (Usuario u : usuarios)
    {
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        readyLatch.countDown();
        try
        {
          startLatch.await();
          actividadesService.unirseActividad(actividadId, u.getId());
        } catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }, executor);

      futures.add(future);
    }

    readyLatch.await(5, TimeUnit.SECONDS);
    startLatch.countDown();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
    executor.shutdown();

    // Assert: los 20 se anotaron + 1 organizador = 21 participantes persistidos en Mongo
    Actividad guardada = actividadesRepository.findById(actividadId).orElseThrow();
    assertThat(guardada.getParticipantes()).hasSize(21);

    for (Usuario u : usuarios)
      assertThat(guardada.getParticipantes()).contains(u);
  }

  @Test
  @DisplayName("Con 4 lugares libres y 20 altas simultáneas entran exactamente 4 y 16 reciben 'sin cupo' claro")
  void unirseActividad_cuatroLugaresLibresYVeinteAltasSimultaneas_entranCuatroYSinSobrecupo() throws Exception
  {
    // Arrange: maximo 5 participantes, organizador ocupa 1 -> quedan exactamente 4 lugares libres
    Usuario organizador = usuarioRepository.save(new Usuario("organizador", "pass", TipoRol.USER));

    Actividad actividad = new Actividad(
        "Tenis Dobles", "Torneo relampago", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Palermo", -34.58, -58.43),
        LocalDateTime.now().plusDays(2), 2, LocalDateTime.now(), 2, 5, organizador
    );
    actividad.cambiarEstado(TipoEstadoActividad.PROPUESTA);
    actividad = actividadesRepository.save(actividad);
    String actividadId = actividad.getId();

    int cantidadUsuarios = 20;
    List<Usuario> usuarios = new ArrayList<>();
    for (int i = 1; i <= cantidadUsuarios; i++)
      usuarios.add(usuarioRepository.save(new Usuario("user-competidor-" + i, "pass", TipoRol.USER)));

    // Act: 20 hilos intentan sumarse simultaneamente contra MongoDB real
    ExecutorService executor = Executors.newFixedThreadPool(cantidadUsuarios);
    CountDownLatch readyLatch = new CountDownLatch(cantidadUsuarios);
    CountDownLatch startLatch = new CountDownLatch(1);

    AtomicInteger exitos = new AtomicInteger(0);
    AtomicInteger sinCupo = new AtomicInteger(0);
    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (Usuario u : usuarios)
    {
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        readyLatch.countDown();
        try
        {
          startLatch.await();
          actividadesService.unirseActividad(actividadId, u.getId());
          exitos.incrementAndGet();
        } catch (CapacidadMaximaException e)
        {
          sinCupo.incrementAndGet();
        } catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }, executor);

      futures.add(future);
    }

    readyLatch.await(5, TimeUnit.SECONDS);
    startLatch.countDown();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
    executor.shutdown();

    // Assert: entran exactamente 4, exactamente 16 reciben CapacidadMaximaException
    assertThat(exitos.get())
        .as("Deben anotarse exactamente 4 participantes")
        .isEqualTo(4);

    assertThat(sinCupo.get())
        .as("Los 16 restantes deben recibir CapacidadMaximaException (sin cupo)")
        .isEqualTo(16);

    Actividad guardada = actividadesRepository.findById(actividadId).orElseThrow();
    assertThat(guardada.getParticipantes())
        .as("Nunca debe haber sobrecupo en MongoDB (organizador + 4 = 5)")
        .hasSize(5);
  }
}
