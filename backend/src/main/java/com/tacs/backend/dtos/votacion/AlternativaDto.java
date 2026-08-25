package com.tacs.backend.dtos.votacion;

import com.tacs.backend.domain.clima.Clima;

import java.time.LocalDateTime;

public record AlternativaDto(Long id, LocalDateTime fecha, Clima clima, int numeroAlternativa) {
}
