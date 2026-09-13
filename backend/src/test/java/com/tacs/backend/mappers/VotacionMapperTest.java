package com.tacs.backend.mappers;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.domain.votacion.Alternativa;
import com.tacs.backend.domain.votacion.Votacion;
import com.tacs.backend.dtos.votacion.AlternativaDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VotacionMapperTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);

  private final VotacionMapper mapper = new VotacionMapper(new ActividadesMapper());

  @Test
  void cumpleReglasClimaEsNullCuandoLaActividadNoTieneReglasDefinidas()
  {
    Votacion votacion = crearVotacion(null);
    Alternativa alternativa = crearAlternativa(new Clima(80, 22, 10)); // clima malo, pero no hay reglas para comparar

    AlternativaDto dto = mapper.alternativaToAlternativaDto(alternativa, votacion);

    assertThat(dto.cumpleReglasClima()).isNull();
  }

  @Test
  void cumpleReglasClimaEsTrueCuandoElClimaDeLaAlternativaCumple()
  {
    Votacion votacion = crearVotacion(new ReglasClima(30, 10, 30, 20));
    Alternativa alternativa = crearAlternativa(new Clima(5, 22, 10)); // dentro de todos los limites

    AlternativaDto dto = mapper.alternativaToAlternativaDto(alternativa, votacion);

    assertThat(dto.cumpleReglasClima()).isTrue();
  }

  @Test
  void cumpleReglasClimaEsFalseCuandoElClimaDeLaAlternativaNoCumple()
  {
    Votacion votacion = crearVotacion(new ReglasClima(30, 10, 30, 20));
    Alternativa alternativa = crearAlternativa(new Clima(80, 22, 10)); // 80% de lluvia > 30% permitido

    AlternativaDto dto = mapper.alternativaToAlternativaDto(alternativa, votacion);

    assertThat(dto.cumpleReglasClima()).isFalse();
  }

  private Votacion crearVotacion(ReglasClima reglasClima)
  {
    Actividad actividad = new Actividad(
        "Asado en el parque", "desc", TipoActividad.AIRE_LIBRE, UBICACION,
        LocalDateTime.now().plusDays(1), 2, LocalDateTime.now(), 2, 10, null);
    actividad.setReglasClima(reglasClima);

    Votacion votacion = new Votacion();
    votacion.setActividad(actividad);
    return votacion;
  }

  private Alternativa crearAlternativa(Clima clima)
  {
    Alternativa alternativa = new Alternativa();
    alternativa.setId(1L);
    alternativa.setNumeroAltenativa(1);
    alternativa.setFecha(LocalDateTime.now().plusDays(2));
    alternativa.setClima(clima);
    return alternativa;
  }
}
