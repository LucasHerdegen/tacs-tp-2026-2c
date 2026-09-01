package com.tacs.backend.services;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.dtos.clima.PronosticoRespuestaDto;

import java.time.LocalDate;
import java.util.List;

public interface ActividadesService
{
  ActividadDto createActividad(ActividadPostDto actividadPostDto, Long usuarioId);

  List<ActividadDto> buscarActividades(TipoActividad tipo, String barrio, LocalDate fecha);

  public void unirseActividad(Long actividadId, Long usuarioId);

  public void bajarseActividad(Long actividadId, Long usuarioId);

  public PronosticoRespuestaDto obtenerClimaActividad(Long actividadId, Long usuarioId);

  //metodo para ver organizadas + participadas, separadas en 3 metodos por performance
  List<ActividadDto> actividadesOrganizadas(Long usuarioId, TipoEstadoActividad estado);

  List<ActividadDto> actividadesParticipadas(Long usuarioId, TipoEstadoActividad estado);

  List<ActividadDto> actividadesDelUsuario(Long usuarioId, TipoEstadoActividad estado);

  void cancelarActividad(Long actividadId, Long usuarioId);

  ActividadDto actualizarConfiguracionClima(Long actividadId, Long usuarioId, ConfigurarCondicionesDto dto);
}
