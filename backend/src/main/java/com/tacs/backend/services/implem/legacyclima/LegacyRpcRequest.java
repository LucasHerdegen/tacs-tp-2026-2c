package com.tacs.backend.services.implem.legacyclima;

public record LegacyRpcRequest(
    String id,
    String method,
    ParametrosRpc params)
{
  public static LegacyRpcRequest crearPronostico(String id, double latitud, double longitud, String timestampIso)
  {
    return new LegacyRpcRequest(
        id,
        "weather.forecast",
        new ParametrosRpc(new UbicacionRpc(latitud, longitud), timestampIso));
  }

  public record ParametrosRpc(
      UbicacionRpc location,
      String timestamp)
  {
  }

  public record UbicacionRpc(
      double lat,
      double lon)
  {
  }
}
