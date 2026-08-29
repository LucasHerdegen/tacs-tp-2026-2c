package com.tacs.backend.mappers;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.usuario.UsuarioDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Component
public class ActividadesMapper
{
  public Actividad actividadPostDtoToActividad(ActividadPostDto dto, Usuario organizador)
  {
    Actividad actividad = new Actividad(
        dto.titulo(),
        dto.descripcion(),
        dto.tipoActividad(),
        dto.ubicacion(),
        dto.fecha(),
        LocalDateTime.now(),
        dto.cantidadMinima(),
        dto.cantidadMaxima(),
        organizador
    );

    actividad.setReglasClima(dto.reglasClima());
    actividad.setHorasAnticipacion(dto.horasAnticipacion() != null ? dto.horasAnticipacion() : 0);
    actividad.setRangoReprogramacion(dto.rangoReprogramacion());

    return actividad;
  }

  public ActividadDto actividadToActividadDto(Actividad actividad)
  {
    return new ActividadDto(
        actividad.getId(),
        actividad.getTitulo(),
        actividad.getDescripcion(),
        actividad.getTipo(),
        actividad.getUbicacion(),
        actividad.getFecha(),
        actividad.getMinimoParticipantes(),
        actividad.getMaximoParticipantes(),
        usuarioToUsuarioDto(actividad.getOrganizador()),
        actividad.getParticipantes().stream()
            .map(this::usuarioToUsuarioDto)
            .collect(Collectors.toList()),
        actividad.getHorasAnticipacion(),
        actividad.getRangoReprogramacion(),
        actividad.getCambiosFecha(),
        actividad.getEstado() != null ? actividad.getEstado().getTipo() : null,
        actividad.getReglasClima()
    );
  }

  public UsuarioDto usuarioToUsuarioDto(Usuario usuario)
  {
    if (usuario == null) return null;
    return new UsuarioDto(usuario.getId(), usuario.getUsername(), usuario.getRol());
  }
}
