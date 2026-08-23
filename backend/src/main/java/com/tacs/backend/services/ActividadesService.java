package com.tacs.backend.services;

import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;

public interface ActividadesService
{
  ActividadDto createActividad(ActividadPostDto actividadPostDto);
}
