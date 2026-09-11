package com.tacs.backend.dtos.votacion;

import jakarta.validation.constraints.Min;

public record VotoPostDto(
    @Min(value = 1, message = "Debe indicar el numero de alternativa")
    int numeroAlternativa)
{
}
