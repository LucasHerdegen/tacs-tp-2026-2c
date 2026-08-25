package com.tacs.backend.services;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;

import java.time.LocalDate;
import java.util.List;

public interface ActividadesService
{
  ActividadDto createActividad(ActividadPostDto actividadPostDto);

  List<ActividadDto> buscarActividades(TipoActividad tipo, String barrio, LocalDate fecha);

  public void unirseActividad(Long actividadId, Long usuarioId);

  public void bajarseActividad(Long actividadId, Long usuarioId);
}
