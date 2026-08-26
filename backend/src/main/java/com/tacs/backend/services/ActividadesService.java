package com.tacs.backend.services;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;

import java.util.List;

public interface ActividadesService
{
  ActividadDto createActividad(ActividadPostDto actividadPostDto);

  //metodo para ver organizadas + participadas, separadas en 3 metodos por performance
  List<ActividadDto> actividadesOrganizadas(Long usuarioId, TipoEstadoActividad estado);

  List<ActividadDto> actividadesParticipadas(Long usuarioId, TipoEstadoActividad estado);

  List<ActividadDto> actividadesDelUsuario(Long usuarioId, TipoEstadoActividad estado);
}
