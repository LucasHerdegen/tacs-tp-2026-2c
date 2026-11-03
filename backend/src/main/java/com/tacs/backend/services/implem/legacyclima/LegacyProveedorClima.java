package com.tacs.backend.services.implem.legacyclima;

import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import com.tacs.backend.services.ProveedorClima;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Implementacion de {@link ProveedorClima} que consume el servicio legacy
 * (Humedad, Datos y Pronosticos S.A.). Normaliza las coordenadas a 2 decimales
 * y trunca la hora antes de delegar en {@link LegacyForecastClient}.
 */
@Slf4j
@Service("legacyProveedorClima")
@Profile("!mock-clima")
public class LegacyProveedorClima implements ProveedorClima
{
  private static final double LATITUD_DEFAULT_CABA = -34.60;
  private static final double LONGITUD_DEFAULT_CABA = -58.38;

  private static final Map<String, double[]> COORDENADAS_POR_BARRIO = Map.of(
      "palermo", new double[] {-34.58, -58.43},
      "belgrano", new double[] {-34.56, -58.46},
      "recoleta", new double[] {-34.59, -58.39},
      "caballito", new double[] {-34.62, -58.44},
      "almagro", new double[] {-34.61, -58.42},
      "san telmo", new double[] {-34.62, -58.37},
      "nuñez", new double[] {-34.54, -58.46},
      "villa urquiza", new double[] {-34.57, -58.49});

  private final RestClient restClient;
  private final LegacyForecastClient forecastClient;

  public LegacyProveedorClima(
      @Qualifier("legacyClimaRestClient") RestClient restClient,
      LegacyForecastClient forecastClient)
  {
    this.restClient = restClient;
    this.forecastClient = forecastClient;
  }

  @Override
  public Clima obtenerClima(Ubicacion ubicacion)
  {
    return obtenerPronostico(ubicacion, LocalDateTime.now());
  }

  @Override
  public Clima obtenerPronostico(Ubicacion ubicacion, LocalDateTime fechaHorario)
  {
    double[] coords = resolverCoordenadas(ubicacion);
    double latitudRedondeada = redondearDosDecimales(coords[0]);
    double longitudRedondeada = redondearDosDecimales(coords[1]);
    LocalDateTime fechaHoraTruncada = (fechaHorario != null ? fechaHorario : LocalDateTime.now())
        .truncatedTo(ChronoUnit.HOURS);

    return forecastClient.consultarPronosticoCacheado(
        latitudRedondeada,
        longitudRedondeada,
        fechaHoraTruncada);
  }

  public EstadoProveedorClima verificarSalud()
  {
    boolean healthOk = consultarEndpointSalud("/healthz");
    if (!healthOk)
      return EstadoProveedorClima.CAIDO;

    boolean readyOk = consultarEndpointSalud("/readyz");
    return readyOk ? EstadoProveedorClima.OK : EstadoProveedorClima.DEGRADADO;
  }

  static double redondearDosDecimales(double valor)
  {
    return Math.round(valor * 100.0) / 100.0;
  }

  static double[] resolverCoordenadas(Ubicacion ubicacion)
  {
    if (ubicacion != null && ubicacion.getLatitud() != null && ubicacion.getLongitud() != null)
      return new double[] {ubicacion.getLatitud(), ubicacion.getLongitud()};

    if (ubicacion != null && ubicacion.getCiudad() != null)
    {
      String clave = ubicacion.getCiudad().trim().toLowerCase();
      double[] conocidas = COORDENADAS_POR_BARRIO.get(clave);
      if (conocidas != null)
        return conocidas;
    }

    return new double[] {LATITUD_DEFAULT_CABA, LONGITUD_DEFAULT_CABA};
  }

  private boolean consultarEndpointSalud(String path)
  {
    try
    {
      ResponseEntity<String> respuesta = restClient.get()
          .uri(path)
          .retrieve()
          .toEntity(String.class);
      return respuesta.getStatusCode().is2xxSuccessful();
    } catch (HttpStatusCodeException e)
    {
      return false;
    } catch (Exception e)
    {
      log.debug("Fallo sondeando {} en proveedor legacy: {}", path, e.getMessage());
      return false;
    }
  }
}
