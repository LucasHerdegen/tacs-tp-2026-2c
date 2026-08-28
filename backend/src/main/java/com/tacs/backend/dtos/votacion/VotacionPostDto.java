package com.tacs.backend.dtos.votacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record VotacionPostDto(@Min(value = 1, message = "El quorum minimo debe ser mayor a 0")
                              int quorumMinimo,
                              @NotEmpty(message = "Debe proponerse al menos una alternativa")
                              @Valid
                              List<AlternativaPostDto> alternativas) {
}