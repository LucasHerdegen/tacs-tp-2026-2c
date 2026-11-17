package com.tacs.backend.repositories;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

/**
 * Valida y normaliza parametros de ordenamiento ({@link Sort}) contra listas blancas de campos
 * permitidos, evitando que MongoDB ignore silenciosamente propiedades inexistentes.
 */
public final class ValidacionOrdenamiento
{
  private static final Map<String, String> CAMPOS_ACTIVIDAD = Map.ofEntries(
      Map.entry("id", "id"),
      Map.entry("titulo", "titulo"),
      Map.entry("descripcion", "descripcion"),
      Map.entry("tipo", "tipo"),
      Map.entry("tipoActividad", "tipo"),
      Map.entry("ubicacion.ciudad", "ubicacion.ciudad"),
      Map.entry("ubicacion.latitud", "ubicacion.latitud"),
      Map.entry("ubicacion.longitud", "ubicacion.longitud"),
      Map.entry("fecha", "fechaRealizacion"),
      Map.entry("fechaRealizacion", "fechaRealizacion"),
      Map.entry("fechaCreacion", "fechaCreacion"),
      Map.entry("duracionEstimada", "duracionEstimada"),
      Map.entry("minimoParticipantes", "minimoParticipantes"),
      Map.entry("maximoParticipantes", "maximoParticipantes"),
      Map.entry("horasAnticipacion", "horasAnticipacion"),
      Map.entry("estado", "estado"),
      Map.entry("estadoActividad", "estado")
  );

  private static final Map<String, String> CAMPOS_NOTIFICACION = Map.of(
      "id", "id",
      "usuarioId", "usuarioId",
      "contenido", "contenido",
      "tipo", "tipo",
      "actividadId", "actividadId",
      "votacionId", "votacionId",
      "leida", "leida",
      "fechaCreacion", "fechaCreacion",
      "fechaLectura", "fechaLectura"
  );

  private ValidacionOrdenamiento()
  {
  }

  public static void validarActividades(Pageable pageable)
  {
    validar(pageable, CAMPOS_ACTIVIDAD);
  }

  public static Pageable normalizarActividades(Pageable pageable)
  {
    return normalizar(pageable, CAMPOS_ACTIVIDAD);
  }

  public static void validarNotificaciones(Pageable pageable)
  {
    validar(pageable, CAMPOS_NOTIFICACION);
  }

  public static Pageable normalizarNotificaciones(Pageable pageable)
  {
    return normalizar(pageable, CAMPOS_NOTIFICACION);
  }

  private static void validar(Pageable pageable, Map<String, String> camposPermitidos)
  {
    if (pageable == null || pageable.isUnpaged() || pageable.getSort().isUnsorted())
      return;

    for (Sort.Order order : pageable.getSort())
      resolverCampo(order.getProperty(), camposPermitidos);
  }

  private static Pageable normalizar(Pageable pageable, Map<String, String> camposPermitidos)
  {
    if (pageable == null || pageable.isUnpaged() || pageable.getSort().isUnsorted())
      return pageable;

    List<Sort.Order> ordenes = pageable.getSort().stream()
        .map(order ->
        {
          String campo = resolverCampo(order.getProperty(), camposPermitidos);
          return campo.equals(order.getProperty()) ? order : order.withProperty(campo);
        })
        .toList();

    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(ordenes));
  }

  private static String resolverCampo(String propiedad, Map<String, String> camposPermitidos)
  {
    String campo = camposPermitidos.get(propiedad);
    if (campo == null)
      throw new IllegalArgumentException("El campo de ordenamiento '" + propiedad + "' no existe");
    return campo;
  }
}
