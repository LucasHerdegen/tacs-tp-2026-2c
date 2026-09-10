package com.tacs.backend.dtos.votacion;

import com.tacs.backend.dtos.actividades.ActividadResumenDto;

import java.time.LocalDateTime;
import java.util.List;

public record VotacionDto(Long id,
                          LocalDateTime fechaApertura,
                          LocalDateTime fechaLimite,
                          LocalDateTime fechaCierre,
                          ActividadResumenDto actividad,
                          List<AlternativaDto> alternativasDtos,
                          int quorumMinimo,
                          boolean abierta,
                          AlternativaDto alternativaGanadora)
{
}