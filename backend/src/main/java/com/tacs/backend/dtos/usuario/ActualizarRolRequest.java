package com.tacs.backend.dtos.usuario;

import com.tacs.backend.domain.usuario.TipoRol;
import jakarta.validation.constraints.NotNull;

public record ActualizarRolRequest(@NotNull TipoRol rol)
{
}
