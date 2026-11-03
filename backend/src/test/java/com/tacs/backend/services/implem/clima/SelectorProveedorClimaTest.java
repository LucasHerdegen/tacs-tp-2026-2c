package com.tacs.backend.services.implem.clima;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.services.implem.clima.ContextoProveedorClima.DatosContexto;
import com.tacs.backend.services.implem.legacyclima.LegacyProveedorClima;
import com.tacs.backend.services.implem.weatherapi.WeatherApiProveedorClima;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectorProveedorClimaTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);
  private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 28, 16, 0);

  @Mock
  private WeatherApiProveedorClima weatherApiProveedorClima;

  @Mock
  private LegacyProveedorClima legacyProveedorClima;

  private MonitorProveedoresClima monitor;
  private SelectorProveedorClima selector;

  @BeforeEach
  void setUp()
  {
    monitor = new MonitorProveedoresClima(CircuitBreakerRegistry.ofDefaults(), null);
    selector = new SelectorProveedorClima(weatherApiProveedorClima, legacyProveedorClima, monitor);
  }

  @AfterEach
  void tearDown()
  {
    ContextoProveedorClima.limpiar();
  }

  @Test
  void cuandoElUsuarioEligeWeatherApiUsaWeatherApiSinLlamarALegacy()
  {
    Clima esperado = new Clima(10.0, 22.0, 12.0);
    when(weatherApiProveedorClima.obtenerPronostico(UBICACION, FECHA)).thenReturn(esperado);

    ContextoProveedorClima.iniciar("user-1", TipoProveedorClima.WEATHER_API);
    Clima obtenido = selector.obtenerPronostico(UBICACION, FECHA);

    assertThat(obtenido).isEqualTo(esperado);
    verify(legacyProveedorClima, never()).obtenerPronostico(UBICACION, FECHA);
    DatosContexto snapshot = ContextoProveedorClima.obtenerSnapshot();
    assertThat(snapshot.proveedorSeleccionado()).isEqualTo(TipoProveedorClima.WEATHER_API);
    assertThat(snapshot.proveedorUtilizado()).isEqualTo(TipoProveedorClima.WEATHER_API);
    assertThat(snapshot.fallbackActivo()).isFalse();
  }

  @Test
  void cuandoElUsuarioEligeLegacyYFuncionaDevuelveElClimaDeLegacy()
  {
    Clima esperadoLegacy = new Clima(35.0, 19.5, 18.0);
    when(legacyProveedorClima.obtenerPronostico(UBICACION, FECHA)).thenReturn(esperadoLegacy);

    ContextoProveedorClima.iniciar("user-2", TipoProveedorClima.LEGACY);
    Clima obtenido = selector.obtenerPronostico(UBICACION, FECHA);

    assertThat(obtenido).isEqualTo(esperadoLegacy);
    verify(weatherApiProveedorClima, never()).obtenerPronostico(UBICACION, FECHA);
    DatosContexto snapshot = ContextoProveedorClima.obtenerSnapshot();
    assertThat(snapshot.proveedorSeleccionado()).isEqualTo(TipoProveedorClima.LEGACY);
    assertThat(snapshot.proveedorUtilizado()).isEqualTo(TipoProveedorClima.LEGACY);
    assertThat(snapshot.fallbackActivo()).isFalse();
  }

  @Test
  void cuandoElUsuarioEligeLegacyYFallaActivaFallbackAutomaticoAWeatherApiYRegistraMetrica()
  {
    Clima respaldoWeatherApi = new Clima(15.0, 21.0, 10.0);
    when(legacyProveedorClima.obtenerPronostico(UBICACION, FECHA))
        .thenThrow(new ProveedorClimaIndisponibleException("Legacy caido 503"));
    when(weatherApiProveedorClima.obtenerPronostico(UBICACION, FECHA))
        .thenReturn(respaldoWeatherApi);

    ContextoProveedorClima.iniciar("user-2", TipoProveedorClima.LEGACY);
    Clima obtenido = selector.obtenerPronostico(UBICACION, FECHA);

    assertThat(obtenido).isEqualTo(respaldoWeatherApi);
    DatosContexto snapshot = ContextoProveedorClima.obtenerSnapshot();
    assertThat(snapshot.proveedorSeleccionado()).isEqualTo(TipoProveedorClima.LEGACY);
    assertThat(snapshot.proveedorUtilizado()).isEqualTo(TipoProveedorClima.WEATHER_API);
    assertThat(snapshot.fallbackActivo()).isTrue();
    assertThat(snapshot.mensajeEstado()).contains("WeatherAPI");

    var statsLegacy = monitor.construirEstadisticas(TipoProveedorClima.LEGACY);
    assertThat(statsLegacy.fallbacksActivados()).isEqualTo(1);
  }

  @Test
  void cuandoFallaLegacyYTambienFallaElFallbackPropagaProveedorClimaIndisponibleException()
  {
    when(legacyProveedorClima.obtenerClima(UBICACION))
        .thenThrow(new ProveedorClimaIndisponibleException("Legacy caido"));
    when(weatherApiProveedorClima.obtenerClima(UBICACION))
        .thenThrow(new ProveedorClimaIndisponibleException("WeatherAPI tambien caido"));

    ContextoProveedorClima.iniciar("user-2", TipoProveedorClima.LEGACY);

    assertThatThrownBy(() -> selector.obtenerClima(UBICACION))
        .isInstanceOf(ProveedorClimaIndisponibleException.class);
  }
}
