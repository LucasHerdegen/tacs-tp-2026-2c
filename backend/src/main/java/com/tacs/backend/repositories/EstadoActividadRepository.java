package com.tacs.backend.repositories;

import com.tacs.backend.domain.actividad.EstadoActividad;
import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadoActividadRepository extends JpaRepository<EstadoActividad, Long>
{
  Optional<EstadoActividad> findByTipo(TipoEstadoActividad tipo);
}
