package com.tacs.backend.dtos.votacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record VotacionPostDto(@Min(value = 1, message = "El quorum minimo debe ser mayor a 0")
                              int quorumMinimo,
                              @NotNull(message = "La fecha limite de la votacion es requerida")
                              @Future(message = "La fecha limite debe ser futura")
                              LocalDateTime fechaLimite,
                              @NotEmpty(message = "Debe proponerse al menos una alternativa")
                              @Valid
                              List<AlternativaPostDto> alternativas) {
}
