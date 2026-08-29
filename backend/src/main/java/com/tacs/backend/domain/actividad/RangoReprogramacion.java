package com.tacs.backend.domain.actividad;

import com.tacs.backend.dtos.actividades.RangoReprogramacionDto;
import com.tacs.backend.exceptions.RangoReprogramacionInvalidoException;

import jakarta.persistence.Embeddable;
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
  private int dias;
  private int horaInicio;
  private int horaFinal;

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
