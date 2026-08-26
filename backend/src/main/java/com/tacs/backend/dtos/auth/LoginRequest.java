package com.tacs.backend.dtos.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "El username es requerido") String username,
    @NotBlank(message = "La password es requerida") String password)
{
}
