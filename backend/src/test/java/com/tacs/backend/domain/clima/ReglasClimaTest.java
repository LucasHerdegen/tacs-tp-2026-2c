package com.tacs.backend.domain.clima;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasClimaTest
{
  private Clima clima(double probabilidadLluvia, double temperatura, double viento)
  {
    return new Clima(probabilidadLluvia, temperatura, viento);
  }

  @Test
  void sinNingunaReglaConfiguradaCualquierClimaEsFavorable()
  {
    ReglasClima reglas = new ReglasClima();

    assertThat(reglas.esFavorable(clima(100, -40, 200))).isTrue();
  }

  @Test
  void conTodasLasReglasConfiguradasSeComportaComoAntes()
  {
    ReglasClima reglas = new ReglasClima(30.0, 10.0, 30.0, 20.0);

    assertThat(reglas.esFavorable(clima(20, 20, 10))).isTrue();
    assertThat(reglas.esFavorable(clima(50, 20, 10))).isFalse();
    assertThat(reglas.esFavorable(clima(20, 5, 10))).isFalse();
    assertThat(reglas.esFavorable(clima(20, 35, 10))).isFalse();
    assertThat(reglas.esFavorable(clima(20, 20, 25))).isFalse();
  }

  @Test
  void unaReglaSinConfigurarNoRestringeEsaDimension()
  {
    ReglasClima soloLluvia = new ReglasClima(30.0, null, null, null);

    assertThat(soloLluvia.esFavorable(clima(20, -40, 200))).isTrue();
    assertThat(soloLluvia.esFavorable(clima(50, -40, 200))).isFalse();
  }

  @Test
  void actualizarConTodoNullNoModificaNadaYQuedaSinRestricciones()
  {
    ReglasClima reglas = new ReglasClima();

    reglas.actualizar(null, null, null, null);

    assertThat(reglas.getMaxProbabilidadLluvia()).isNull();
    assertThat(reglas.getMinTemperatura()).isNull();
    assertThat(reglas.getMaxTemperatura()).isNull();
    assertThat(reglas.getMaxViento()).isNull();
    assertThat(reglas.esFavorable(clima(100, -40, 200))).isTrue();
  }

  @Test
  void actualizarPreservaLosCamposNoTocados()
  {
    ReglasClima reglas = new ReglasClima(30.0, 10.0, 30.0, 20.0);

    reglas.actualizar(null, 5.0, null, null);

    assertThat(reglas.getMaxProbabilidadLluvia()).isEqualTo(30.0);
    assertThat(reglas.getMinTemperatura()).isEqualTo(5.0);
    assertThat(reglas.getMaxTemperatura()).isEqualTo(30.0);
    assertThat(reglas.getMaxViento()).isEqualTo(20.0);
  }
}
