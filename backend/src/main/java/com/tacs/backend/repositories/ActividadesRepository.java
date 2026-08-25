package com.tacs.backend.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActividadesRepository extends JpaRepository<Actividad, Long>
{
  List<Actividad> findByOrganizadorId(Long organizadorId);
  List<Actividad> findByOrganizadorIdAndEstadoTipo(Long organizadorId, TipoEstadoActividad tipo);
  List<Actividad> findByParticipantesId(Long usuarioId);
  List<Actividad> findByParticipantesIdAndEstadoTipo(Long usuarioId, TipoEstadoActividad tipo);

}