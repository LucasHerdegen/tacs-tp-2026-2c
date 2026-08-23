package com.tacs.backend.domain.clima;

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
}
