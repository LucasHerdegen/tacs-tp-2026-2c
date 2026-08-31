package com.tacs.backend.domain.actividad;

import com.tacs.backend.dtos.actividades.RangoReprogramacionDto;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RangoReprogramacion
{
  @Min(value = 1, message = "La cantidad de dias debe ser mayor a 0")
  private int dias;

  @Min(value = 0, message = "La hora de inicio debe estar entre 0 y 23")
  @Max(value = 23, message = "La hora de inicio debe estar entre 0 y 23")
  private int horaInicio;

  @Min(value = 0, message = "La hora final debe estar entre 0 y 23")
  @Max(value = 23, message = "La hora final debe estar entre 0 y 23")
  private int horaFinal;

  @AssertTrue(message = "La hora de inicio debe ser menor o igual a la hora final")
  public boolean isRangoHorarioValido()
  {
    return horaInicio <= horaFinal;
  }
  
  public void actualizar(RangoReprogramacionDto dto) {
    if (dto == null) return;

    if (dto.dias() != null) {
        this.dias = dto.dias();
    }

    int nuevaHoraInicio = dto.horaInicio() != null ? dto.horaInicio() : this.horaInicio;
    int nuevaHoraFinal = dto.horaFinal() != null ? dto.horaFinal() : this.horaFinal;

    if (nuevaHoraFinal <= nuevaHoraInicio) {
        throw new RangoReprogramacionInvalidoException(
            "La hora final (" + nuevaHoraFinal + "hs) debe ser mayor a la hora de inicio (" + nuevaHoraInicio + "hs)"
        );
    }

    this.horaInicio = nuevaHoraInicio;
    this.horaFinal = nuevaHoraFinal;
  }
}
