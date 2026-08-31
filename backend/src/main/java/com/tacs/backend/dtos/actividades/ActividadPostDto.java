package com.tacs.backend.dtos.actividades;

import com.tacs.backend.domain.actividad.RangoReprogramacion;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.ReglasClima;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * reglasClima, horasAnticipacion y rangoReprogramacion son opcionales: una
 * actividad TECHADA, por ejemplo, no necesita monitoreo de clima. Si
 * reglasClima queda sin cargar, la actividad directamente no entra al
 * chequeo periodico (ver ActividadesRepository.findCandidatasParaChequeoClima).
 */
public record ActividadPostDto(@NotBlank(message = "El titulo es requerido") String titulo,
                               String descripcion,
                               @NotNull(message = "El tipo de actividad es requerido") TipoActividad tipoActividad,
                               @NotNull(message = "La ubicacion es requerida") @Valid Ubicacion ubicacion,
                               @NotNull(message = "La fecha es requerida") @Future(message = "La fecha debe ser futura") LocalDateTime fecha,
                               @Min(value = 1, message = "La duracion minima es 1") int duracionEstimada,
                               @Min(value = 2, message = "La actividad debe de contar con por lo menos 2 personas") int cantidadMinima,
                               ReglasClima reglasClima,
                               Integer horasAnticipacion,
                               @Valid RangoReprogramacion rangoReprogramacion,
                               @Min(value = 2, message = "La cantidad maxima debe ser por lo menos 2") int cantidadMaxima)
{
}
