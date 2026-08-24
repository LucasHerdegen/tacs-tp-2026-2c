package com.tacs.backend.dtos.actividades;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ActividadPostDto(@NotBlank(message = "El titulo es requerido") String titulo,
                               String descripcion,
                               TipoActividad tipoActividad,
                               @NotNull(message = "La ubicacion es requerida") Ubicacion ubicacion,
                               @NotNull(message = "La fecha es requerida") @Future(message = "La fecha debe ser futura") LocalDateTime fecha,
                               @Min(value = 2, message = "La actividad debe de contar con por lo menos 2 personas") int cantidadMinima,
                               int cantidadMaxima)
{
}
