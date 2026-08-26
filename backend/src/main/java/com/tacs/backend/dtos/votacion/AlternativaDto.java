package com.tacs.backend.dtos.votacion;

import com.tacs.backend.dtos.clima.ClimaDto;

import java.time.LocalDateTime;

public record AlternativaDto(Long id, LocalDateTime fecha, ClimaDto clima, int numeroAlternativa) {
}
