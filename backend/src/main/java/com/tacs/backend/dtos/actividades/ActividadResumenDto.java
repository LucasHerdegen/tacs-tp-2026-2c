package com.tacs.backend.dtos.actividades;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;

import java.time.LocalDateTime;

public record ActividadResumenDto(
    Long id,
    String titulo,
    TipoEstadoActividad estado,
    LocalDateTime fechaRealizacion
)
{
}
