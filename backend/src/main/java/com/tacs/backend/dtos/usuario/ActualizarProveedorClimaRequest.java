package com.tacs.backend.dtos.usuario;

import com.tacs.backend.domain.clima.TipoProveedorClima;
import jakarta.validation.constraints.NotNull;

public record ActualizarProveedorClimaRequest(
    @NotNull(message = "El proveedor de clima es obligatorio")
    TipoProveedorClima proveedorClima)
{
}
