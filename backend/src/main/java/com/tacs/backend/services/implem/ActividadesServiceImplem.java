package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.RangoReprogramacion;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.ConfigurarCondicionesDto;
import com.tacs.backend.exceptions.AccesoDenegadoException;
import com.tacs.backend.exceptions.ActividadNotFoundException;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import com.tacs.backend.services.ActividadesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
class ActividadesServiceImplem implements ActividadesService
{
  private final ActividadesRepository actividadesRepository;
  private final UsuarioRepository usuarioRepository;
  private final ActividadesMapper actividadesMapper;

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


