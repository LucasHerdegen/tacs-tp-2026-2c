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
