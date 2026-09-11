package com.tacs.backend.dtos.actividades;

import com.tacs.backend.domain.actividad.TipoEstadoActividad;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoDto(
    @NotNull(message = "El estado es requerido")
    TipoEstadoActividad estado
)
{
}
