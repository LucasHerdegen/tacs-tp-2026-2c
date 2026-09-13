package com.tacs.backend.persistence.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CambioFechaEntity
{
  private LocalDateTime fecha;
  private LocalDateTime fechaAntigua;
  private LocalDateTime fechaNueva;
}
