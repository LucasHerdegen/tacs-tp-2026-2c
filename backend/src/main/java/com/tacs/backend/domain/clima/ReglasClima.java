package com.tacs.backend.domain.clima;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ReglasClima
{
  private Double maxProbabilidadLluvia;
  private Double minTemperatura;
  private Double maxTemperatura;
  private Double maxViento;

  public boolean esFavorable(Clima clima)
  {
    return noSuperaMaximo(maxProbabilidadLluvia, clima.getProbabilidadLluvia())
        && noBajaDelMinimo(minTemperatura, clima.getTemperatura())
        && noSuperaMaximo(maxTemperatura, clima.getTemperatura())
        && noSuperaMaximo(maxViento, clima.getViento());
  }

  private boolean noSuperaMaximo(Double maximo, double valorReal)
  {
    return maximo == null || valorReal <= maximo;
  }

  private boolean noBajaDelMinimo(Double minimo, double valorReal)
  {
    return minimo == null || valorReal >= minimo;
  }

  public void actualizar(Double maxProbabilidadLluvia, Double minTemperatura, Double maxTemperatura, Double maxViento)
  {
    if (maxProbabilidadLluvia != null)
      this.maxProbabilidadLluvia = maxProbabilidadLluvia;
    if (minTemperatura != null)
      this.minTemperatura = minTemperatura;
    if (maxTemperatura != null)
      this.maxTemperatura = maxTemperatura;
    if (maxViento != null)
      this.maxViento = maxViento;
  }
}
