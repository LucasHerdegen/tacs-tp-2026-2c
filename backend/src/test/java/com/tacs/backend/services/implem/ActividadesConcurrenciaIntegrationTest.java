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
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.NotificacionInboxService;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.ServicioNotificaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActividadesConcurrenciaIntegrationTest
{
  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private ProveedorClima proveedorClima;

  @Mock
  private ClimaMapper climaMapper;

  @Mock
  private ServicioNotificaciones servicioNotificaciones;

  @Mock
  private NotificacionInboxService notificacionInboxService;

  private ActividadesServiceImplem actividadesService;
  private FakeConcurrentActividadesRepository fakeActividadesRepository;
  private ActividadesMapper actividadesMapper;

  @BeforeEach
  void setUp()
  {
    fakeActividadesRepository = new FakeConcurrentActividadesRepository();
    actividadesMapper = new ActividadesMapper();
    OptimisticLockingRetry retry = new OptimisticLockingRetry(60);

    actividadesService = new ActividadesServiceImplem(
        fakeActividadesRepository,
        usuarioRepository,
        actividadesMapper,
        proveedorClima,
        climaMapper,
        servicioNotificaciones,
        notificacionInboxService,
        retry
    );
  }

  @Test
  @DisplayName("20 altas simultáneas con 28 lugares libres anotan exitosamente a los 20")
  void veinteAltasSimultaneasConLugaresLibresAnotanALosVeinte() throws Exception
  {
    // Arrange: actividad con organizador (1 ocupado) y maximo 30 (29 lugares libres)
    Usuario organizador = new Usuario("organizador", "pass", TipoRol.USER);
    organizador.setId("org-1");

    Actividad actividad = new Actividad(
        "Fútbol 5", "Picado de los viernes", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Palermo", -34.58, -58.43),
        LocalDateTime.now().plusDays(2), 2, LocalDateTime.now(), 2, 30, organizador
    );
    actividad.setId("act-1");
    actividad.setVersion(0L);
    fakeActividadesRepository.save(actividad);

    int cantidadUsuarios = 20;
    List<Usuario> usuarios = new ArrayList<>();
    for (int i = 1; i <= cantidadUsuarios; i++)
    {
      String uid = "user-" + i;
      Usuario u = new Usuario(uid, "pass", TipoRol.USER);
      u.setId(uid);
      usuarios.add(u);
      when(usuarioRepository.existsById(uid)).thenReturn(true);
      when(usuarioRepository.findById(uid)).thenReturn(Optional.of(u));
    }

    // Act: 20 hilos intentan unirse exactamente al mismo tiempo
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
          actividadesService.unirseActividad("act-1", u.getId());
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
    startLatch.countDown(); // Disparar en paralelo

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(15, TimeUnit.SECONDS);
    executor.shutdown();

    // Assert: los 20 se anotaron + 1 organizador = 21 participantes
    Actividad guardada = fakeActividadesRepository.findById("act-1").orElseThrow();
    assertThat(guardada.getParticipantes()).hasSize(21);

    for (Usuario u : usuarios)
    {
      assertThat(guardada.getParticipantes()).contains(u);
    }
  }

  @Test
  @DisplayName("Con 4 lugares libres y 20 altas simultáneas entran exactamente 4 y 16 reciben 'sin cupo' claro")
  void conCuatroLugaresLibresEntranExactamenteCuatroYSobrecupoNuncaOcurre() throws Exception
  {
    // Arrange: maximo 5 participantes, organizador ocupa 1 -> quedan exactamente 4 lugares libres
    Usuario organizador = new Usuario("organizador", "pass", TipoRol.USER);
    organizador.setId("org-1");

    Actividad actividad = new Actividad(
        "Tenis Dobles", "Torneo relampago", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Palermo", -34.58, -58.43),
        LocalDateTime.now().plusDays(2), 2, LocalDateTime.now(), 2, 5, organizador
    );
    actividad.setId("act-2");
    actividad.setVersion(0L);
    fakeActividadesRepository.save(actividad);

    int cantidadUsuarios = 20;
    List<Usuario> usuarios = new ArrayList<>();
    for (int i = 1; i <= cantidadUsuarios; i++)
    {
      String uid = "user-competidor-" + i;
      Usuario u = new Usuario(uid, "pass", TipoRol.USER);
      u.setId(uid);
      usuarios.add(u);
      when(usuarioRepository.existsById(uid)).thenReturn(true);
      when(usuarioRepository.findById(uid)).thenReturn(Optional.of(u));
    }

    // Act: 20 hilos intentan sumarse simultaneamente
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
          actividadesService.unirseActividad("act-2", u.getId());
          exitos.incrementAndGet();
        }
        catch (CapacidadMaximaException e)
        {
          sinCupo.incrementAndGet();
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
    startLatch.countDown(); // Disparar concurrencia

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(15, TimeUnit.SECONDS);
    executor.shutdown();

    // Assert: entran exactamente 4, exactamente 16 reciben CapacidadMaximaException
    assertThat(exitos.get())
        .as("Deben anotarse exactamente 4 participantes")
        .isEqualTo(4);

    assertThat(sinCupo.get())
        .as("Los 16 restantes deben recibir CapacidadMaximaException (sin cupo)")
        .isEqualTo(16);

    Actividad guardada = fakeActividadesRepository.findById("act-2").orElseThrow();
    assertThat(guardada.getParticipantes())
        .as("Nunca debe haber sobrecupo (organizador + 4 = 5)")
        .hasSize(5);
  }

  private static class FakeConcurrentActividadesRepository implements ActividadesRepository
  {
    private final AtomicReference<Actividad> storage = new AtomicReference<>();

    @Override
    public synchronized Actividad save(Actividad actividad)
    {
      Actividad current = storage.get();
      if (current != null && !Objects.equals(actividad.getVersion(), current.getVersion()))
      {
        throw new OptimisticLockingFailureException(
            "Conflicto de version en Actividad: entidad tenia " + actividad.getVersion()
                + " pero guardada tiene " + current.getVersion()
        );
      }

      Actividad cloned = cloneActividad(actividad);
      cloned.setVersion((cloned.getVersion() == null ? 0L : cloned.getVersion()) + 1L);
      storage.set(cloned);
      return cloneActividad(cloned);
    }

    @Override
    public synchronized Optional<Actividad> findById(String id)
    {
      return Optional.ofNullable(storage.get()).map(this::cloneActividad);
    }

    @Override
    public Page<Actividad> buscarActividades(TipoActividad tipo, String busqueda, LocalDate fecha,
                                             TipoEstadoActividad estado, Boolean cupoDisponible, Pageable pageable)
    {
      return Page.empty();
    }

    @Override
    public List<Actividad> findByOrganizadorId(String organizadorId)
    {
      return List.of();
    }

    @Override
    public List<Actividad> findByOrganizadorIdAndEstado(String organizadorId, TipoEstadoActividad estado)
    {
      return List.of();
    }

    @Override
    public List<Actividad> findByParticipantesId(String usuarioId)
    {
      return List.of();
    }

    @Override
    public List<Actividad> findByParticipantesIdAndEstado(String usuarioId, TipoEstadoActividad estado)
    {
      return List.of();
    }

    @Override
    public List<Actividad> findByOrganizadorIdOrParticipantesId(String usuarioId)
    {
      return List.of();
    }

    @Override
    public List<Actividad> findByOrganizadorIdOrParticipantesIdAndEstado(String usuarioId, TipoEstadoActividad estado)
    {
      return List.of();
    }

    @Override
    public long countByEstado(TipoEstadoActividad estado)
    {
      return 0;
    }

    @Override
    public List<Actividad> findCandidatasParaChequeoClima()
    {
      return List.of();
    }

    @Override
    public List<Actividad> findByEstadoAndRecordatorioEnviadoFalse(TipoEstadoActividad estado)
    {
      return List.of();
    }

    @Override
    public List<Actividad> findCandidatasParaRecordatorio()
    {
      return List.of();
    }

    @Override
    public List<Actividad> findCandidatasParaFinalizacion(LocalDateTime now)
    {
      return List.of();
    }

    @Override
    public List<Actividad> findAll()
    {
      return List.of();
    }

    @Override
    public long count()
    {
      return 0;
    }

    private Actividad cloneActividad(Actividad a)
    {
      if (a == null) return null;
      Actividad c = new Actividad();
      c.setId(a.getId());
      c.setVersion(a.getVersion());
      c.setTitulo(a.getTitulo());
      c.setDescripcion(a.getDescripcion());
      c.setTipo(a.getTipo());
      c.setUbicacion(a.getUbicacion());
      c.setFechaCreacion(a.getFechaCreacion());
      c.setFechaRealizacion(a.getFechaRealizacion());
      c.setDuracionEstimada(a.getDuracionEstimada());
      c.setMinimoParticipantes(a.getMinimoParticipantes());
      c.setMaximoParticipantes(a.getMaximoParticipantes());
      c.setRecordatorioEnviado(a.isRecordatorioEnviado());
      c.setOrganizador(a.getOrganizador());
      c.setParticipantes(new ArrayList<>(a.getParticipantes()));
      c.setHorasAnticipacion(a.getHorasAnticipacion());
      c.setRangoReprogramacion(a.getRangoReprogramacion());
      c.setCambiosFecha(new ArrayList<>(a.getCambiosFecha()));
      c.setEstado(a.getEstado());
      c.setReglasClima(a.getReglasClima());
      return c;
    }
  }
}
