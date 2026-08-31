package com.tacs.backend.dtos.votacion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VotoPostDto(@NotNull(message = "El usuario es requerido") Long usuarioId,
                          @Min(value = 1, message = "Debe indicar el numero de alternativa")
                          int numeroAlternativa) {
}