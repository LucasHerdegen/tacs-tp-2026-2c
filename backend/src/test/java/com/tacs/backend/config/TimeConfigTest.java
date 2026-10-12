package com.tacs.backend.config;

import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.UbicacionDto;
import jakarta.validation.ClockProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
  void clockTieneZonaConfigurada()
  {
    TimeConfig timeConfig = new TimeConfig("America/Argentina/Buenos_Aires");

    Clock clock = timeConfig.clock();

    assertEquals(ZoneId.of("America/Argentina/Buenos_Aires"), clock.getZone());
  }

  @Test
  void clockProviderDevuelveElClockInyectado()
  {
    TimeConfig timeConfig = new TimeConfig("America/Argentina/Buenos_Aires");
    Clock clock = timeConfig.clock();

    ClockProvider clockProvider = timeConfig.clockProvider(clock);

    assertEquals(clock, clockProvider.getClock());
  }

  @Test
  void initSeteaTimeZonePorDefecto()
  {
    TimeConfig timeConfig = new TimeConfig("America/Argentina/Buenos_Aires");

    timeConfig.init();

    assertEquals(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"), TimeZone.getDefault());
  }

  @Test
  void validacionActividadParaDentroDeUnaHoraEsValidaConClockProviderBuenosAires()
  {
    // Fijamos un instante: 15:00 UTC = 12:00 en Buenos Aires (UTC-3)
    Instant fixedInstant = Instant.parse("2026-10-12T15:00:00Z");
    ZoneId zoneBuenosAires = ZoneId.of("America/Argentina/Buenos_Aires");
    Clock fixedClock = Clock.fixed(fixedInstant, zoneBuenosAires);

    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
        .configure()
        .clockProvider(() -> fixedClock)
        .buildValidatorFactory();
    Validator validator = validatorFactory.getValidator();

    // Actividad para dentro de 1 hora en Buenos Aires (13:00)
    LocalDateTime fechaDentroDeUnaHora = LocalDateTime.of(2026, 10, 12, 13, 0, 0);

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

    // Actividad en el pasado en Buenos Aires (11:00)
    LocalDateTime fechaEnElPasado = LocalDateTime.of(2026, 10, 12, 11, 0, 0);
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
