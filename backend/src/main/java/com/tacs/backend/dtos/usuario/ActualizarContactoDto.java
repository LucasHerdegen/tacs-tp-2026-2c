package com.tacs.backend.dtos.usuario;

import com.tacs.backend.domain.usuario.MedioContacto;
import jakarta.validation.constraints.NotNull;

public record ActualizarContactoDto(
    @NotNull(message = "El contacto es requerido")
    MedioContacto medioContacto
)
{
}
