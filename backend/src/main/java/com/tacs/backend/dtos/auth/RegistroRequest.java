package com.tacs.backend.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
    @NotBlank(message = "El username es requerido")
    @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
    String username,

    @NotBlank(message = "La password es requerida")
    @Size(min = 8, max = 72, message = "La password debe tener entre 8 y 72 caracteres")
    String password)
{
}
