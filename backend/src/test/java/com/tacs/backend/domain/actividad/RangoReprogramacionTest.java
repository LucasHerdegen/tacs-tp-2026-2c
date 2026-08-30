package com.tacs.backend.domain.actividad;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
Estos constraints solo se ejercitan de verdad cuando ActividadPostDto.rangoReprogramacion
llega con @Valid desde el controller; acá se testea el propio RangoReprogramacion
de forma aislada, sin levantar el contexto de Spring.*/
class RangoReprogramacionTest
{
  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void initValidator()
  {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void cerrarValidator()
  {
    factory.close();
  }

  @Test
  void esValidoConDiasYHorasDentroDeRango()
  {
    RangoReprogramacion rango = new RangoReprogramacion(3, 10, 20);

    assertThat(validator.validate(rango)).isEmpty();
  }

  @Test
  void esInvalidoConDiasMenorOIgualACero()
  {
    RangoReprogramacion rango = new RangoReprogramacion(0, 10, 20);

    Set<ConstraintViolation<RangoReprogramacion>> violaciones = validator.validate(rango);

    assertThat(violaciones).isNotEmpty();
  }

  @Test
  void esInvalidoConHoraFueraDelRango0A23()
  {
    RangoReprogramacion rango = new RangoReprogramacion(3, 10, 99);

    Set<ConstraintViolation<RangoReprogramacion>> violaciones = validator.validate(rango);

    assertThat(violaciones).isNotEmpty();
  }

  @Test
  void esInvalidoConHoraInicioMayorAHoraFinal()
  {
    RangoReprogramacion rango = new RangoReprogramacion(3, 20, 10);

    Set<ConstraintViolation<RangoReprogramacion>> violaciones = validator.validate(rango);

    assertThat(violaciones).isNotEmpty();
  }
}