package com.tacs.backend.services.implem.clima;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.TipoProveedorClima;
import com.tacs.backend.exceptions.ProveedorClimaIndisponibleException;
import com.tacs.backend.services.ProveedorClima;
import com.tacs.backend.services.implem.legacyclima.LegacyProveedorClima;
import com.tacs.backend.services.implem.weatherapi.WeatherApiProveedorClima;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * Enrutador primario de {@link ProveedorClima} que selecciona dinamicamente el
 * proveedor configurado por el usuario u organizador en {@link ContextoProveedorClima}.
 * Si el usuario selecciono {@link TipoProveedorClima#LEGACY} y este falla, aplica
 * fallback automatico a {@link WeatherApiProveedorClima} dejando trazabilidad
 * en el contexto y en {@link MonitorProveedoresClima}.
 */
@Slf4j
@Primary
@Service
@Profile("!mock-clima")
@RequiredArgsConstructor
public class SelectorProveedorClima implements ProveedorClima
{
  private static final String MENSAJE_FALLBACK_LEGACY =
      "El proveedor Legacy no estuvo disponible; se utilizó WeatherAPI como respaldo automático.";

  private final WeatherApiProveedorClima weatherApiProveedorClima;
  private final LegacyProveedorClima legacyProveedorClima;
  private final MonitorProveedoresClima monitorProveedoresClima;

  @Override
  public Clima obtenerClima(Ubicacion ubicacion)
  {
    return ejecutarConSeleccionYFallback(proveedor -> proveedor.obtenerClima(ubicacion));
  }

  @Override
  public Clima obtenerPronostico(Ubicacion ubicacion, LocalDateTime fechaHorario)
  {
    return ejecutarConSeleccionYFallback(proveedor -> proveedor.obtenerPronostico(ubicacion, fechaHorario));
  }

  private Clima ejecutarConSeleccionYFallback(Function<ProveedorClima, Clima> operacion)
  {
    TipoProveedorClima preferido = ContextoProveedorClima.obtenerProveedorSeleccionado();
    if (preferido != TipoProveedorClima.LEGACY)
    {
      ContextoProveedorClima.registrarProveedorUtilizado(TipoProveedorClima.WEATHER_API);
      return operacion.apply(weatherApiProveedorClima);
    }

    try
    {
      Clima resultado = operacion.apply(legacyProveedorClima);
      ContextoProveedorClima.registrarProveedorUtilizado(TipoProveedorClima.LEGACY);
      return resultado;
    } catch (ProveedorClimaIndisponibleException ex)
    {
      log.warn("Fallo en proveedor LEGACY ({}), activando fallback automatico a WeatherAPI", ex.getMessage());
      if (monitorProveedoresClima != null)
        monitorProveedoresClima.registrarFallback(TipoProveedorClima.LEGACY);
      ContextoProveedorClima.registrarFallback(TipoProveedorClima.WEATHER_API, MENSAJE_FALLBACK_LEGACY);
      return operacion.apply(weatherApiProveedorClima);
    }
  }
}
