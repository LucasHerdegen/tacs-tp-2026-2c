package com.tacs.backend.services.implem.clima;

import com.tacs.backend.domain.clima.TipoProveedorClima;

import java.time.Duration;

/**
 * Contexto asociado al hilo actual que transporta el usuario solicitante,
 * su proveedor de clima preferido, el presupuesto de tiempo disponible para
 * la operacion y el resultado de ejecucion (proveedor efectivamente utilizado,
 * activacion de fallback o degradacion).
 */
public final class ContextoProveedorClima
{
  private static final ThreadLocal<DatosContexto> ACTUAL = new ThreadLocal<>();
  private static final ThreadLocal<Long> LIMITE_TIEMPO_NANOS = new ThreadLocal<>();

  private ContextoProveedorClima()
  {
  }

  public static void iniciar(String usuarioId, TipoProveedorClima proveedorSeleccionado)
  {
    iniciarConPresupuesto(usuarioId, proveedorSeleccionado, null);
  }

  public static void iniciarConPresupuesto(
      String usuarioId,
      TipoProveedorClima proveedorSeleccionado,
      Duration presupuestoTotal)
  {
    TipoProveedorClima efectivo = proveedorSeleccionado != null
        ? proveedorSeleccionado
        : TipoProveedorClima.WEATHER_API;
    ACTUAL.set(new DatosContexto(usuarioId, efectivo, efectivo, false, false, null));
    if (presupuestoTotal != null && !presupuestoTotal.isNegative())
      LIMITE_TIEMPO_NANOS.set(System.nanoTime() + presupuestoTotal.toNanos());
    else
      LIMITE_TIEMPO_NANOS.remove();
  }

  /**
   * Devuelve el tiempo restante del presupuesto de la request actual, o
   * {@code null} si no se configuro un presupuesto global para el hilo.
   * Cuando el plazo ya vencio devuelve {@link Duration#ZERO}.
   */
  public static Duration obtenerTiempoRestante()
  {
    Long limiteNanos = LIMITE_TIEMPO_NANOS.get();
    if (limiteNanos == null)
      return null;
    long restanteNanos = limiteNanos - System.nanoTime();
    return restanteNanos <= 0L ? Duration.ZERO : Duration.ofNanos(restanteNanos);
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
    LIMITE_TIEMPO_NANOS.remove();
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
