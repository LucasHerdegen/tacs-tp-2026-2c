package com.tacs.backend.repositories;

import com.tacs.backend.domain.actividad.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActividadesRepository extends JpaRepository<Actividad, Long>
{
}
