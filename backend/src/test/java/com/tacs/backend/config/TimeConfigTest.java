package com.tacs.backend.config;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.UbicacionDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeConfigTest
{
  private final TimeZone timeZoneOriginal = TimeZone.getDefault();

  @AfterEach
  void tearDown()
  {
    TimeZone.setDefault(timeZoneOriginal);
  }

  @Test
  void initSeteaTimeZonePorDefecto()
  {
    TimeConfig timeConfig = new TimeConfig("America/Argentina/Buenos_Aires");

    timeConfig.init();

    assertEquals(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"), TimeZone.getDefault());
  }

  @Test
  void validacionActividadParaDentroDeUnaHoraEsValidaTrasInitTimeConfig()
  {
    TimeConfig timeConfig = new TimeConfig("America/Argentina/Buenos_Aires");
    timeConfig.init();

    ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    Validator validator = validatorFactory.getValidator();

    LocalDateTime fechaDentroDeUnaHora = LocalDateTime.now().plusHours(1);

    ActividadPostDto dtoFuturo = new ActividadPostDto(
        "Fútbol 5",
        "Partido con amigos",
        TipoActividad.AIRE_LIBRE,
        new UbicacionDto("CABA", -34.6037, -58.3816),
        fechaDentroDeUnaHora,
        60,
        10,
        10
    );

    Set<ConstraintViolation<ActividadPostDto>> violationsFuturo = validator.validate(dtoFuturo);
    assertTrue(violationsFuturo.isEmpty(), "La actividad programada para dentro de 1 hora debe ser valida");

    LocalDateTime fechaEnElPasado = LocalDateTime.now().minusHours(1);
    ActividadPostDto dtoPasado = new ActividadPostDto(
        "Fútbol 5",
        "Partido con amigos",
        TipoActividad.AIRE_LIBRE,
        new UbicacionDto("CABA", -34.6037, -58.3816),
        fechaEnElPasado,
        60,
        10,
        10
    );

    Set<ConstraintViolation<ActividadPostDto>> violationsPasado = validator.validate(dtoPasado);
    assertThat(violationsPasado)
        .extracting(ConstraintViolation::getMessage)
        .contains("La fecha debe ser futura");
  }
}

