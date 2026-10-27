package com.tacs.backend.services.implem.clima;

import com.tacs.backend.domain.clima.TipoProveedorClima;

/**
 * Contexto asociado al hilo actual que transporta el usuario solicitante,
 * su proveedor de clima preferido y el resultado de ejecucion (proveedor
 * efectivamente utilizado, activacion de fallback o degradacion).
 */
public final class ContextoProveedorClima
{
  private static final ThreadLocal<DatosContexto> ACTUAL = new ThreadLocal<>();

  private ContextoProveedorClima()
  {
  }

  public static void iniciar(String usuarioId, TipoProveedorClima proveedorSeleccionado)
  {
    TipoProveedorClima efectivo = proveedorSeleccionado != null
        ? proveedorSeleccionado
        : TipoProveedorClima.WEATHER_API;
    ACTUAL.set(new DatosContexto(usuarioId, efectivo, efectivo, false, false, null));
  }

  public static String obtenerUsuarioId()
  {
    DatosContexto datos = ACTUAL.get();
    return datos != null ? datos.usuarioId() : null;
  }

  public static TipoProveedorClima obtenerProveedorSeleccionado()
  {
    DatosContexto datos = ACTUAL.get();
    return datos != null ? datos.proveedorSeleccionado() : TipoProveedorClima.WEATHER_API;
  }

  public static void registrarProveedorUtilizado(TipoProveedorClima proveedorUtilizado)
  {
    DatosContexto datos = ACTUAL.get();
    if (datos == null)
      return;
    if (!datos.fallbackActivo())
      ACTUAL.set(new DatosContexto(
          datos.usuarioId(),
          datos.proveedorSeleccionado(),
          proveedorUtilizado,
          false,
          datos.degradado(),
          datos.mensajeEstado()));
  }

  public static void registrarFallback(TipoProveedorClima proveedorRespaldo, String mensaje)
  {
    DatosContexto datos = ACTUAL.get();
    if (datos == null)
      ACTUAL.set(new DatosContexto(
          null,
          TipoProveedorClima.LEGACY,
          proveedorRespaldo,
          true,
          false,
          mensaje));
    else
      ACTUAL.set(new DatosContexto(
          datos.usuarioId(),
          datos.proveedorSeleccionado(),
          proveedorRespaldo,
          true,
          datos.degradado(),
          mensaje));
  }

  public static void registrarDegradacion(String mensaje)
  {
    DatosContexto datos = ACTUAL.get();
    if (datos == null)
      ACTUAL.set(new DatosContexto(
          null,
          TipoProveedorClima.WEATHER_API,
          TipoProveedorClima.WEATHER_API,
          false,
          true,
          mensaje));
    else
      ACTUAL.set(new DatosContexto(
          datos.usuarioId(),
          datos.proveedorSeleccionado(),
          datos.proveedorUtilizado(),
          datos.fallbackActivo(),
          true,
          mensaje));
  }

  public static DatosContexto obtenerSnapshot()
  {
    DatosContexto datos = ACTUAL.get();
    if (datos != null)
      return datos;
    return new DatosContexto(
        null,
        TipoProveedorClima.WEATHER_API,
        TipoProveedorClima.WEATHER_API,
        false,
        false,
        null);
  }

  public static void limpiar()
  {
    ACTUAL.remove();
  }

  public record DatosContexto(
      String usuarioId,
      TipoProveedorClima proveedorSeleccionado,
      TipoProveedorClima proveedorUtilizado,
      boolean fallbackActivo,
      boolean degradado,
      String mensajeEstado)
  {
  }
}
