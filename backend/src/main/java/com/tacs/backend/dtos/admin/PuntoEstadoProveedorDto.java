package com.tacs.backend.dtos.admin;

import java.time.Instant;

public record PuntoEstadoProveedorDto(
    Instant timestamp,
    EstadoProveedorClima estado,
    String detalle)
{
}
