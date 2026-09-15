package com.tacs.backend.mappers;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.ActividadResumenDto;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import org.springframework.stereotype.Component;
import com.tacs.backend.dtos.actividades.UbicacionDto;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import com.tacs.backend.domain.actividad.Ubicacion;

@Component
public class ActividadesMapper
{
  public Actividad actividadPostDtoToActividad(ActividadPostDto dto, Usuario organizador)
  {
    return new Actividad(
        dto.titulo(),
        dto.descripcion(),
        dto.tipoActividad(),
        ubicacionDtoToUbicacion(dto.ubicacion()),
        dto.fecha(),
        dto.duracionEstimada(),
        LocalDateTime.now(),
        dto.cantidadMinima(),
        dto.cantidadMaxima(),
        organizador
    );
  }

  public ActividadDto actividadToActividadDto(Actividad actividad)
  {
    return new ActividadDto(
        actividad.getId(),
        actividad.getTitulo(),
        actividad.getDescripcion(),
        actividad.getTipo(),
        ubicacionToUbicacionDto(actividad.getUbicacion()),
        actividad.getFechaRealizacion(),
        actividad.getDuracionEstimada(),
        actividad.getMinimoParticipantes(),
        actividad.getMaximoParticipantes(),
        usuarioToUsuarioDto(actividad.getOrganizador()),
        actividad.getParticipantes().stream()
            .map(this::usuarioToUsuarioDto)
            .collect(Collectors.toList()),
        actividad.getHorasAnticipacion(),
        actividad.getRangoReprogramacion(),
        actividad.getCambiosFecha(),
        actividad.getEstado(),
        actividad.getReglasClima()
    );
  }

  public ActividadResumenDto actividadToActividadResumenDto(Actividad actividad)
  {
    if (actividad == null)
      return null;

    return new ActividadResumenDto(
        actividad.getId(),
        actividad.getTitulo(),
        actividad.getEstado(),
        actividad.getFechaRealizacion()
    );
  }

  /**
   * Usado para embeber el organizador/participantes dentro de un ActividadDto,
   * visible para cualquiera que pueda ver la actividad (no solo el propio
   * usuario). medioContacto es informacion privada (ej. chat_id de Telegram):
   * no se propaga aca. Solo se expone en el DTO de "mi propio perfil"
   * (AuthServiceImplem.toDto, usado por GET /usuarios/me y el PATCH de contacto).
   */
  public UsuarioDto usuarioToUsuarioDto(Usuario usuario)
  {
    if (usuario == null)
      return null;

    return new UsuarioDto(usuario.getId(), usuario.getUsername(), usuario.getRol(), null);
  }

  public Ubicacion ubicacionDtoToUbicacion(UbicacionDto dto)
  {
    if (dto == null) return null;
    return new Ubicacion(dto.getBarrio(), dto.getLatitud(), dto.getLongitud());
  }

  public UbicacionDto ubicacionToUbicacionDto(Ubicacion ubicacion)
  {
    if (ubicacion == null) return null;
    return new UbicacionDto(ubicacion.getBarrio(), ubicacion.getLatitud(),
        ubicacion.getLongitud());
  }
}
