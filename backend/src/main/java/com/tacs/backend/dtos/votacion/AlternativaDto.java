package com.tacs.backend.dtos.votacion;

import com.tacs.backend.dtos.clima.ClimaDto;

import java.time.LocalDateTime;

/**
 * cumpleReglasClima es null cuando la actividad no tiene ReglasClima definidas
 * (no aplica, ej. actividad techada); true/false cuando si las tiene, indicando
 * si el pronostico de esta alternativa las cumple.
 */
public record AlternativaDto(Long id,
                             LocalDateTime fecha,
                             ClimaDto clima,
                             int numeroAlternativa,
                             long cantidadVotos,
                             Boolean cumpleReglasClima) {
}