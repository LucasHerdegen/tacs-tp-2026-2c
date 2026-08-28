package com.tacs.backend.dtos.votacion;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AlternativaPostDto(@NotNull(message = "La fecha de la alternativa es requerida")
                                 @Future(message = "La fecha debe ser futura")
                                 LocalDateTime fecha) {
}