package com.tacs.backend.persistence.entities;

import com.tacs.backend.dtos.admin.EstadoProveedorClima;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PuntoEstadoProveedorEntity
{
  private Instant timestamp;
  private EstadoProveedorClima estado;
  private String detalle;
}
