package com.tacs.backend.services;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;

import java.util.List;

public interface ActividadesService
{
  ActividadDto createActividad(ActividadPostDto actividadPostDto);

  // ---- ver actividades organizadas + participadas por usuario ~~ User story #12
  //estado es opcional, si viene null se listan todas las actividades (con todos los estados)
  //ver implementacion
  List<ActividadDto> actividadesOrganizadas(Long usuarioId, TipoEstadoActividad estado);

  List<ActividadDto> actividadesParticipadas(Long usuarioId, TipoEstadoActividad estado);
}
