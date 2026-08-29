package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.RangoReprogramacion;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.clima.ClimaDto;
import com.tacs.backend.dtos.clima.PronosticoRespuestaDto;
import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.CapacidadMaximaException;
import com.tacs.backend.exceptions.NoParticipanteException;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.ActividadesService;
import com.tacs.backend.services.ProveedorClima;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

import com.tacs.backend.mappers.ClimaMapper;

@RequiredArgsConstructor
@Service
public class ActividadesServiceImplem implements ActividadesService
{
  private final ActividadesRepository actividadesRepository;
  private final UsuarioRepository usuarioRepository;
  private final ActividadesMapper actividadesMapper;
  private final ProveedorClima proveedorClima;
  private final ClimaMapper climaMapper;

  @Override
  @Transactional
  public ActividadDto createActividad(ActividadPostDto actividadPostDto, Long usuarioId)
  {
    if (actividadPostDto.cantidadMinima() > actividadPostDto.cantidadMaxima())
      throw new IllegalArgumentException("La cantidad mínima no puede ser mayor a la máxima");

    var usuario = this.usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new UsuarioNotFoundException("El usuario con id " + usuarioId + " no fue encontrado"));

    var actividad = this.actividadesMapper.actividadPostDtoToActividad(actividadPostDto, usuario);

    actividad.cambiarEstado(TipoEstadoActividad.PROPUESTA);

    actividad = this.actividadesRepository.save(actividad);

    return this.actividadesMapper.actividadToActividadDto(actividad);
  }

  @Override
  public List<ActividadDto> actividadesOrganizadas(Long usuarioId, TipoEstadoActividad estado) {
    validarExistenciaUsuario(usuarioId);

    List<Actividad> actividades = estado != null
            ? actividadesRepository.findByOrganizadorIdAndEstado(usuarioId, estado)
            : actividadesRepository.findByOrganizadorId(usuarioId);

    return actividades.stream()
            .map(actividadesMapper::actividadToActividadDto)
            .toList();
  }

  @Override
  public List<ActividadDto> actividadesParticipadas(Long usuarioId, TipoEstadoActividad estado) {
    validarExistenciaUsuario(usuarioId);

    List<Actividad> actividades = estado != null
            ? actividadesRepository.findByParticipantesIdAndEstado(usuarioId, estado)
            : actividadesRepository.findByParticipantesId(usuarioId);

    return actividades.stream()
            .map(actividadesMapper::actividadToActividadDto)
            .toList();
  }

  private void validarExistenciaUsuario(Long usuarioId) {
    if(!usuarioRepository.existsById(usuarioId))
      throw new UsuarioNotFoundException("El usuario con id: " + usuarioId + " no existe");
  }

  @Override
  public List<ActividadDto> buscarActividades(TipoActividad tipo, String barrio, LocalDate fecha)
  {
    return actividadesRepository.findAll().stream()
        .filter(a -> tipo == null || a.getTipo().equals(tipo))
        .filter(a -> barrio == null || (a.getUbicacion() != null && a.getUbicacion().getBarrio().equalsIgnoreCase(barrio)))
        .filter(a -> fecha == null || a.getFechaRealizacion().toLocalDate().equals(fecha))
        .map(actividadesMapper::actividadToActividadDto)
        .toList();
  }

  @Override
  @Transactional
  public void unirseActividad(Long actividadId, Long usuarioId) {
    validarExistenciaUsuario(usuarioId);

    var actividad = actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));

    if (actividad.getParticipantes().size() >= actividad.getMaximoParticipantes()) {
      throw new CapacidadMaximaException("La actividad ya esta al maximo de participantes permitidos");
    }
    var usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

    actividad.agregarParticipante(usuario);
    actividadesRepository.save(actividad);
  }


  @Override
  @Transactional
  public void bajarseActividad(Long actividadId, Long usuarioId) {
    validarExistenciaUsuario(usuarioId);

    var actividad = actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));

    var usuario = usuarioRepository.findById(usuarioId)
        .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

    actividad.removerParticipante(usuario);
    actividadesRepository.save(actividad);
  }


  @Override
  public PronosticoRespuestaDto obtenerClimaActividad(Long actividadId, Long usuarioId) {
    validarExistenciaUsuario(usuarioId);

    var actividad = actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new ActividadNotFoundException("Actividad no encontrada"));


    boolean esParticipante = actividad.getParticipantes().stream()
        .anyMatch(u -> u.getId().equals(usuarioId));

    if (!esParticipante) {
      throw new NoParticipanteException("Debes ser participante de la actividad para ver su clima");
    }

    ClimaDto climaActual = climaMapper.climaToClimaDto(proveedorClima.obtenerClima(actividad.getUbicacion()));
    ClimaDto pronostico = climaMapper.climaToClimaDto(proveedorClima.obtenerPronostico(actividad.getUbicacion(), actividad.getFechaRealizacion()));

    return new PronosticoRespuestaDto(climaActual, pronostico);
  }

  @Override
  @Transactional
  public void cancelarActividad(Long actividadId, Long usuarioId)
  {
    var actividad = actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new com.tacs.backend.exceptions.ActividadNotFoundException("Actividad no encontrada"));

    if (!actividad.getOrganizador().getId().equals(usuarioId))
      throw new com.tacs.backend.exceptions.AccesoDenegadoException("Solo el organizador puede cancelar la actividad");

    actividad.cambiarEstado(TipoEstadoActividad.CANCELADA);

    actividadesRepository.save(actividad);

    // TODO: Disparar evento o llamar al Notificador para avisar a los participantes (User Story 13)
  }

  @Override
  @Transactional
  public ActividadDto actualizarConfiguracionClima(Long actividadId, Long usuarioId, ConfigurarCondicionesDto dto) {
    Actividad actividad = actividadesRepository.findById(actividadId)
        .orElseThrow(() -> new com.tacs.backend.exceptions.ActividadNotFoundException("Actividad no encontrada"));

    if (!actividad.getOrganizador().getId().equals(usuarioId))
      throw new com.tacs.backend.exceptions.AccesoDenegadoException("Solo el organizador puede configurar el clima");

    if (dto.reglasClima() != null)
      actividad.actualizarReglasClima(dto.reglasClima());

    if (dto.horasAnticipacion() != null)
      actividad.actualizarHorasAnticipacion(dto.horasAnticipacion());

    if (dto.rangoReprogramacion() != null)
      actividad.actualizarRangoReprogramacion(dto.rangoReprogramacion());

    actividadesRepository.save(actividad);

    return actividadesMapper.actividadToActividadDto(actividad);
  }
}


