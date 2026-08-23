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
public class Clima
{
  private double probabilidadLluvia;
  private double temperatura;
  private double viento;

  public boolean probabilidadLluviaEntre(double lower, double upper)
  {
    return probabilidadLluvia >= lower && probabilidadLluvia <= upper;
  }

  public boolean temperaturaEntre(double lower, double upper)
  {
    return temperatura >= lower && temperatura <= upper;
  }

  public boolean vientoEntre(double lower, double upper)
  {
    return viento >= lower && viento <= upper;
  }
}
