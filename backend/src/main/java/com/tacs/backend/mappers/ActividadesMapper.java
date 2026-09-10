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

  public UsuarioDto usuarioToUsuarioDto(Usuario usuario)
  {
    if (usuario == null)
      return null;

    return new UsuarioDto(usuario.getId(), usuario.getUsername(), usuario.getRol());
  }

  public com.tacs.backend.domain.actividad.Ubicacion ubicacionDtoToUbicacion(UbicacionDto dto)
  {
    if (dto == null) return null;
    return new com.tacs.backend.domain.actividad.Ubicacion(dto.getBarrio(), dto.getLatitud(), dto.getLongitud());
  }

  public UbicacionDto ubicacionToUbicacionDto(com.tacs.backend.domain.actividad.Ubicacion ubicacion)
  {
    if (ubicacion == null) return null;
    return new com.tacs.backend.dtos.actividades.UbicacionDto(ubicacion.getBarrio(), ubicacion.getLatitud(),
        ubicacion.getLongitud());
  }
}
