package com.tacs.backend.services;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.dtos.clima.PronosticoRespuestaDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface ActividadesService
{
  ActividadDto createActividad(ActividadPostDto actividadPostDto, String usuarioId);

  ActividadDto obtenerActividad(String id);

  Page<ActividadDto> buscarActividades(TipoActividad tipo, String busqueda, LocalDate fecha,
                                       TipoEstadoActividad estado,
                                       Boolean cupoDisponible, Pageable pageable);

  public void unirseActividad(String actividadId, String usuarioId);

  public void bajarseActividad(String actividadId, String usuarioId);

  public PronosticoRespuestaDto obtenerClimaActividad(String actividadId, String usuarioId);

  //metodo para ver organizadas + participadas, separadas en 3 metodos por performance
  List<ActividadDto> actividadesOrganizadas(String usuarioId, TipoEstadoActividad estado);

  List<ActividadDto> actividadesParticipadas(String usuarioId, TipoEstadoActividad estado);

  List<ActividadDto> actividadesDelUsuario(String usuarioId, TipoEstadoActividad estado);

  void cambiarEstado(String actividadId, String usuarioId, TipoEstadoActividad nuevoEstado);

  ActividadDto actualizarConfiguracionClima(String actividadId, String usuarioId, ConfigurarCondicionesDto dto);
}
