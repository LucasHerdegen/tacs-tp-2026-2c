package com.tacs.backend.domain.actividad;

import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// Las validaciones de forma (dias > 0, horas 0-23, horaInicio <= horaFinal)
// viven en RangoReprogramacionPostDto: esta clase es el value object de
// dominio, sin Bean Validation, siguiendo la misma convencion PostDto
// (input validado) vs. entidad (sin validacion) que el resto del proyecto.

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class RangoReprogramacion
{
  private int dias;
  private int horaInicio;
  private int horaFinal;

  public void actualizar(Integer dias, Integer horaInicio, Integer horaFinal)
  {
    if (dias != null)
    {
      this.dias = dias;
    }

    int nuevaHoraInicio = horaInicio != null ? horaInicio : this.horaInicio;
    int nuevaHoraFinal = horaFinal != null ? horaFinal : this.horaFinal;

    if (nuevaHoraFinal <= nuevaHoraInicio)
    {
      throw new RangoReprogramacionInvalidoException(
          "La hora final (" + nuevaHoraFinal + "hs) debe ser mayor a la hora de inicio (" + nuevaHoraInicio + "hs)"
      );
    }

    this.horaInicio = nuevaHoraInicio;
    this.horaFinal = nuevaHoraFinal;
  }

  public boolean contiene(LocalDateTime fechaRealizacionOriginal, LocalDateTime nuevaFecha)
  {
    long diasDiferencia = ChronoUnit.DAYS.between(
        fechaRealizacionOriginal.toLocalDate(),
        nuevaFecha.toLocalDate()
    );

    if (diasDiferencia <= 0 || diasDiferencia > this.dias)
      return false;

    int hora = nuevaFecha.getHour();
    return hora >= this.horaInicio && hora <= this.horaFinal;
  }
}
