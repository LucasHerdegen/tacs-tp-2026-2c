package com.tacs.backend.services.implem;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.exceptions.UsuarioNotFoundException;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.EstadoActividadRepository;
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
  private final EstadoActividadRepository estadoActividadRepository;
  private final ActividadesMapper actividadesMapper;

  @Override
  @Transactional
  public ActividadDto createActividad(ActividadPostDto actividadPostDto)
  {
    if (actividadPostDto.cantidadMinima() > actividadPostDto.cantidadMaxima())
      throw new IllegalArgumentException("La cantidad mínima no puede ser mayor a la máxima");

    // TODO: reemplazar por una llamada al repo de usuarios con id de JWT
    var usuario = this.usuarioRepository.findById(1L)
        .orElseThrow(() -> new UsuarioNotFoundException("El usuario con id 1 no fue encontrado"));

    var actividad = this.actividadesMapper.actividadPostDtoToActividad(actividadPostDto, usuario);

    var estadoInicial = this.estadoActividadRepository.findByTipo(TipoEstadoActividad.PROPUESTA)
        .orElseThrow(() -> new IllegalStateException("Estado inicial PROPUESTA no configurado en la base de datos"));

    actividad.setEstado(estadoInicial);

    actividad = this.actividadesRepository.save(actividad);

    return this.actividadesMapper.actividadToActividadDto(actividad);
  }

  @Override
  public List<ActividadDto> actividadesOrganizadas(Long usuarioId, TipoEstadoActividad estado) {
    validarExistenciaUsuario(usuarioId);

    List<Actividad> actividades = estado != null
            ? actividadesRepository.findByOrganizadorIdAndEstadoTipo(usuarioId, estado)
            : actividadesRepository.findByOrganizadorId(usuarioId);

    return actividades.stream()
            .map(actividadesMapper::actividadToActividadDto)
            .toList();
  }

  @Override
  public List<ActividadDto> actividadesParticipadas(Long usuarioId, TipoEstadoActividad estado) {
    validarExistenciaUsuario(usuarioId);

    List<Actividad> actividades = estado != null
            ? actividadesRepository.findByParticipantesIdAndEstadoTipo(usuarioId, estado)
            : actividadesRepository.findByParticipantesId(usuarioId);

    return actividades.stream()
            .map(actividadesMapper::actividadToActividadDto)
            .toList();
  }

  private void validarExistenciaUsuario(Long usuarioId) {
    if(!usuarioRepository.existsById(usuarioId))
      throw new UsuarioNotFoundException("El usuario con id: " + usuarioId + " no existe");
  }
}
