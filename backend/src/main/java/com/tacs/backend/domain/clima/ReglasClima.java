package com.tacs.backend.domain.clima;

import com.tacs.backend.dtos.clima.ReglasClimaDto;

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
public class ReglasClima
{
  private double maxProbabilidadLluvia;
  private double minTemperatura;
  private double maxTemperatura;
  private double maxViento;

  public boolean esFavorable(Clima clima)
  {
    return clima.getProbabilidadLluvia() <= maxProbabilidadLluvia &&
        clima.temperaturaEntre(minTemperatura, maxTemperatura) &&
        clima.getViento() <= maxViento;
  }

  public void actualizar(ReglasClimaDto dto)
  {
  if (dto.maxProbabilidadLluvia() != null)
    this.maxProbabilidadLluvia = dto.maxProbabilidadLluvia();

  if (dto.minTemperatura() != null)
    this.minTemperatura = dto.minTemperatura();

  if (dto.maxTemperatura() != null)
    this.maxTemperatura = dto.maxTemperatura();

  if (dto.maxViento() != null)
    this.maxViento = dto.maxViento();
  }
}
