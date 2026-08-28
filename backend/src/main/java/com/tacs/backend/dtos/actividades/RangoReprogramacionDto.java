package com.tacs.backend.dtos.actividades;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RangoReprogramacionDto(
    @Min(value = 0, message = "Los días de reprogramación no deben ser menores a 0")
    Integer dias,

    @Min(value = 0, message = "La hora de inicio no puede ser menor a 0")
    @Max(value = 23, message = "La hora de inicio debe estar entre 0 y 23")
    Integer horaInicio,

    @Min(value = 0, message = "La hora final no puede ser menor a 0")
    @Max(value = 23, message = "La hora final debe estar entre 0 y 23")
    Integer horaFinal
) {}