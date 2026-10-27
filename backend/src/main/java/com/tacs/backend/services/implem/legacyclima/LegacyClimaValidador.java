package com.tacs.backend.services.implem.legacyclima;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.exceptions.RespuestaClimaInvalidaException;
import com.tacs.backend.services.implem.legacyclima.LegacyRpcResponse.ResultadoRpc;
import com.tacs.backend.services.implem.legacyclima.LegacyRpcResponse.UbicacionResultadoRpc;
import org.springframework.stereotype.Component;

/**
 * Valida exhaustivamente el cuerpo devuelto por el proveedor legacy antes de
 * convertirlo al modelo de dominio {@link Clima}. Nunca asume que un HTTP 200
 * implica un JSON valido ni datos meteorologicos coherentes.
 */
@Component
public class LegacyClimaValidador
{
  private static final double MIN_LATITUD = -90.0;
  private static final double MAX_LATITUD = 90.0;
  private static final double MIN_LONGITUD = -180.0;
  private static final double MAX_LONGITUD = 180.0;
  private static final double MIN_TEMPERATURA_C = -90.0;
  private static final double MAX_TEMPERATURA_C = 65.0;
  private static final int MIN_PORCENTAJE = 0;
  private static final int MAX_PORCENTAJE = 100;
  private static final double MIN_PRECIPITACION_MM = 0.0;
  private static final double MIN_VIENTO_KPH = 0.0;
  private static final double MAX_VIENTO_KPH = 400.0;

  private final ObjectMapper objectMapper;

  public LegacyClimaValidador()
  {
    this.objectMapper = new ObjectMapper();
  }

  public Clima validarYMapea(String cuerpoCrudo, String idEsperado)
  {
    if (cuerpoCrudo == null || cuerpoCrudo.isBlank())
      throw new RespuestaClimaInvalidaException("El proveedor legacy devolvio un cuerpo vacio");

    String recorte = cuerpoCrudo.stripLeading();
    if (recorte.startsWith("<"))
      throw new RespuestaClimaInvalidaException("El proveedor legacy devolvio HTML en lugar de JSON");

    LegacyRpcResponse respuesta;
    try
    {
      respuesta = objectMapper.readValue(cuerpoCrudo, LegacyRpcResponse.class);
    } catch (JsonProcessingException e)
    {
      throw new RespuestaClimaInvalidaException("JSON malformado devuelto por el proveedor legacy", e);
    }

    if (respuesta == null)
      throw new RespuestaClimaInvalidaException("Respuesta nula al deserializar JSON del proveedor legacy");

    if (idEsperado != null && !idEsperado.equals(respuesta.id()))
      throw new RespuestaClimaInvalidaException(
          "El id de la respuesta RPC (" + respuesta.id() + ") no coincide con el solicitado (" + idEsperado + ")");

    if (!"OK".equals(respuesta.status()))
    {
      String codigo = respuesta.error() != null ? respuesta.error().code() : "DESCONOCIDO";
      String mensaje = respuesta.error() != null ? respuesta.error().message() : "Sin detalle";
      throw new RespuestaClimaInvalidaException(
          "Respuesta RPC con estado de error (" + codigo + "): " + mensaje);
    }

    ResultadoRpc resultado = respuesta.result();
    if (resultado == null)
      throw new RespuestaClimaInvalidaException("Respuesta RPC con status OK pero sin objeto result");

    validarUbicacion(resultado.location());
    validarCamposMeteorologicos(resultado);

    return new Clima(
        resultado.precipitationProbabilityPct().doubleValue(),
        resultado.temperatureC(),
        resultado.windKph());
  }

  private void validarUbicacion(UbicacionResultadoRpc ubicacion)
  {
    if (ubicacion == null || ubicacion.lat() == null || ubicacion.lon() == null)
      throw new RespuestaClimaInvalidaException("La respuesta RPC no contiene coordenadas validas");

    if (!esFinitoEnRango(ubicacion.lat(), MIN_LATITUD, MAX_LATITUD))
      throw new RespuestaClimaInvalidaException("Latitud fuera de rango en respuesta legacy: " + ubicacion.lat());

    if (!esFinitoEnRango(ubicacion.lon(), MIN_LONGITUD, MAX_LONGITUD))
      throw new RespuestaClimaInvalidaException("Longitud fuera de rango en respuesta legacy: " + ubicacion.lon());
  }

  private void validarCamposMeteorologicos(ResultadoRpc resultado)
  {
    if (resultado.temperatureC() == null
        || resultado.precipitationProbabilityPct() == null
        || resultado.windKph() == null)
      throw new RespuestaClimaInvalidaException("Faltan campos meteorologicos obligatorios en la respuesta legacy");

    if (!esFinitoEnRango(resultado.temperatureC(), MIN_TEMPERATURA_C, MAX_TEMPERATURA_C))
      throw new RespuestaClimaInvalidaException(
          "Temperatura fuera de rango razonable: " + resultado.temperatureC());

    int probLluvia = resultado.precipitationProbabilityPct();
    if (probLluvia < MIN_PORCENTAJE || probLluvia > MAX_PORCENTAJE)
      throw new RespuestaClimaInvalidaException(
          "Probabilidad de precipitacion fuera de rango [0, 100]: " + probLluvia);

    if (resultado.humidityPct() != null
        && (resultado.humidityPct() < MIN_PORCENTAJE || resultado.humidityPct() > MAX_PORCENTAJE))
      throw new RespuestaClimaInvalidaException(
          "Humedad fuera de rango [0, 100]: " + resultado.humidityPct());

    if (resultado.precipitationMm() != null
        && (!Double.isFinite(resultado.precipitationMm()) || resultado.precipitationMm() < MIN_PRECIPITACION_MM))
      throw new RespuestaClimaInvalidaException(
          "Precipitacion en mm invalida: " + resultado.precipitationMm());

    if (!esFinitoEnRango(resultado.windKph(), MIN_VIENTO_KPH, MAX_VIENTO_KPH))
      throw new RespuestaClimaInvalidaException(
          "Velocidad de viento fuera de rango [0, 400]: " + resultado.windKph());
  }

  private boolean esFinitoEnRango(Double valor, double minimo, double maximo)
  {
    return valor != null && Double.isFinite(valor) && valor >= minimo && valor <= maximo;
  }
}
