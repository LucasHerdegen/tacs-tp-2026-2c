package com.tacs.backend.dtos.actividades;
import com.tacs.backend.dtos.clima.ReglasClimaDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

public record ConfigurarCondicionesDto(
    @Valid ReglasClimaDto reglasClima,
    @Min(value = 1, message = "Debe haber por lo menos una hora de anticipacion") Integer horasAnticipacion,
    @Valid RangoReprogramacionDto rangoReprogramacion
) {}