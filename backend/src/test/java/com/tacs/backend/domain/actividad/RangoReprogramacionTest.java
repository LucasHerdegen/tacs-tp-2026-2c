package com.tacs.backend.domain.actividad;

import com.tacs.backend.dtos.actividades.RangoReprogramacionDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/*
  Estos constraints solo se ejercitan de verdad cuando ActividadPostDto.rangoReprogramacion
  llega con @Valid desde el controller; acá se testea el propio RangoReprogramacionDto
  de forma aislada, sin levantar el contexto de Spring. 
*/

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
    RangoReprogramacionDto rango = new RangoReprogramacionDto(3, 10, 20);

    assertThat(validator.validate(rango)).isEmpty();
  }

  @Test
  void esInvalidoConDiasMenorACero()
  {
    RangoReprogramacionDto rango = new RangoReprogramacionDto(-1, 10, 20);

    Set<ConstraintViolation<RangoReprogramacionDto>> violaciones = validator.validate(rango);

    assertThat(violaciones).isNotEmpty();
  }

  @Test
  void esInvalidoConHoraFueraDelRango0A23()
  {
    RangoReprogramacionDto rango = new RangoReprogramacionDto(3, 10, 99);

    Set<ConstraintViolation<RangoReprogramacionDto>> violaciones = validator.validate(rango);

    assertThat(violaciones).isNotEmpty();
  }
}