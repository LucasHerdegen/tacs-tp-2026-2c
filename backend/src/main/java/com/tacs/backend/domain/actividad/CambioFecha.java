package com.tacs.backend.domain.actividad;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CambioFecha
{
  private LocalDateTime fecha;
  private LocalDateTime fechaAntigua;
  private LocalDateTime fechaNueva;
}
