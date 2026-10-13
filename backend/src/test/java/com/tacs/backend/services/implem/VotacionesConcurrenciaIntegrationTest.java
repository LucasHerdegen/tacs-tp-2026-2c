package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.domain.votacion.Voto;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.mappers.VotacionMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.repositories.VotacionesRepository;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VotacionesConcurrenciaIntegrationTest
{
  @Mock
  private ActividadesRepository actividadesRepository;

  @Mock
  private UsuarioRepository usuarioRepository;

  @Mock
  private ProveedorClima proveedorClima;

  @Mock
  private ServicioNotificaciones servicioNotificaciones;

  @Mock
  private NotificacionInboxService notificacionInboxService;

  private VotacionesServiceImplem votacionesService;
  private FakeConcurrentVotacionesRepository fakeVotacionesRepository;
  private VotacionMapper votacionMapper;

  @BeforeEach
  void setUp()
  {
    fakeVotacionesRepository = new FakeConcurrentVotacionesRepository();
    votacionMapper = new VotacionMapper(new ActividadesMapper());
    OptimisticLockingRetry retry = new OptimisticLockingRetry(50);

    votacionesService = new VotacionesServiceImplem(
        fakeVotacionesRepository,
        actividadesRepository,
        usuarioRepository,
        votacionMapper,
        proveedorClima,
        servicioNotificaciones,
        notificacionInboxService,
        retry
    );
  }

  @Test
  @DisplayName("10 votos simultáneos dejan exactamente 10 votos registrados sin perder ninguno")
  void diezVotosSimultaneosDejanDiezVotosRegistrados() throws Exception
  {
    // Arrange
    int cantidadVotantes = 10;
    List<Usuario> votantes = new ArrayList<>();
    for (int i = 1; i <= cantidadVotantes; i++)
    {
      Usuario u = new Usuario("user" + i, "pass", TipoRol.USER);
      u.setId("usr-" + i);
      votantes.add(u);
      when(usuarioRepository.findById("usr-" + i)).thenReturn(Optional.of(u));
    }

    Usuario organizador = new Usuario("org", "pass", TipoRol.USER);
    organizador.setId("org-1");

    Actividad actividad = new Actividad(
        "Fútbol", "picado", TipoActividad.AIRE_LIBRE,
        new Ubicacion("Palermo", -34.58, -58.43),
        LocalDateTime.now().plusDays(2), 2, LocalDateTime.now(), 2, 20, organizador
    );
    actividad.setId("act-1");
    for (Usuario votante : votantes)
    {
      actividad.agregarParticipante(votante);
    }

    Alternativa alt1 = new Alternativa();
    alt1.setId("alt-1");
    alt1.setNumeroAlternativa(1);
    alt1.setFecha(LocalDateTime.now().plusDays(3));

    Alternativa alt2 = new Alternativa();
    alt2.setId("alt-2");
    alt2.setNumeroAlternativa(2);
    alt2.setFecha(LocalDateTime.now().plusDays(4));

    Votacion votacion = new Votacion();
    votacion.setId("vot-1");
    votacion.setVersion(0L);
    votacion.setAbierta(true);
    votacion.setActividad(actividad);
    votacion.agregarAlternativa(alt1);
    votacion.agregarAlternativa(alt2);
    votacion.setQuorumMinimo(2);

    fakeVotacionesRepository.save(votacion);

    // Act: 10 hilos votan al mismo instante
    ExecutorService executor = Executors.newFixedThreadPool(cantidadVotantes);
    CountDownLatch readyLatch = new CountDownLatch(cantidadVotantes);
    CountDownLatch startLatch = new CountDownLatch(1);

    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (int i = 0; i < cantidadVotantes; i++)
    {
      final String usuarioId = votantes.get(i).getId();
      final int alternativaElegida = (i % 2) + 1; // mitad a la 1, mitad a la 2

      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        readyLatch.countDown();
        try
        {
          startLatch.await();
          votacionesService.votar("vot-1", usuarioId, alternativaElegida);
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
    startLatch.countDown(); // Disparar todos los votos en paralelo

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
    executor.shutdown();

    // Assert: los 10 votos estan registrados
    Votacion resultadoFinal = fakeVotacionesRepository.findById("vot-1").orElseThrow();
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

  private static class FakeConcurrentVotacionesRepository implements VotacionesRepository
  {
    private final AtomicReference<Votacion> storage = new AtomicReference<>();

    @Override
    public synchronized Votacion save(Votacion votacion)
    {
      Votacion current = storage.get();
      if (current != null && !Objects.equals(votacion.getVersion(), current.getVersion()))
      {
        throw new OptimisticLockingFailureException(
            "Conflicto de version: entidad tenia " + votacion.getVersion() + " pero guardada tiene " + current.getVersion()
        );
      }

      Votacion cloned = cloneVotacion(votacion);
      cloned.setVersion((cloned.getVersion() == null ? 0L : cloned.getVersion()) + 1L);
      storage.set(cloned);
      return cloneVotacion(cloned);
    }

    @Override
    public synchronized Optional<Votacion> findById(String id)
    {
      return Optional.ofNullable(storage.get()).map(this::cloneVotacion);
    }

    @Override
    public List<Votacion> findByAbiertaAndActividadOrganizadorId(boolean abierta, String organizadorId)
    {
      return List.of();
    }

    @Override
    public List<Votacion> findByAbiertaAndActividadParticipantesId(boolean abierta, String usuarioId)
    {
      return List.of();
    }

    @Override
    public Optional<Votacion> findByAbiertaTrueAndActividadId(String actividadId)
    {
      return findById(actividadId);
    }

    @Override
    public List<Votacion> findByAbiertaTrueAndFechaLimiteBefore(LocalDateTime ahora)
    {
      return List.of();
    }

    @Override
    public List<Votacion> findByAbiertaYUsuarioInvolucrado(boolean abierta, String usuarioId)
    {
      return List.of();
    }

    @Override
    public void delete(Votacion votacion)
    {
      storage.set(null);
    }

    private Votacion cloneVotacion(Votacion original)
    {
      if (original == null) return null;
      Votacion clone = new Votacion();
      clone.setId(original.getId());
      clone.setVersion(original.getVersion());
      clone.setAbierta(original.isAbierta());
      clone.setFechaApertura(original.getFechaApertura());
      clone.setFechaCierre(original.getFechaCierre());
      clone.setFechaLimite(original.getFechaLimite());
      clone.setQuorumMinimo(original.getQuorumMinimo());
      clone.setActividad(original.getActividad());
      clone.setAlternativas(new ArrayList<>(original.getAlternativas()));

      List<Voto> votosClonados = new ArrayList<>();
      for (Voto v : original.getVotos())
      {
        Voto vCopy = new Voto();
        vCopy.setId(v.getId());
        vCopy.setUsuario(v.getUsuario());
        vCopy.setAlternativa(v.getAlternativa());
        votosClonados.add(vCopy);
      }
      clone.setVotos(votosClonados);
      clone.setAlternativaGanadora(original.getAlternativaGanadora());
      return clone;
    }
  }
}
