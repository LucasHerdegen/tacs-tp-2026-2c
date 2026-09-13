package com.tacs.backend.mappers;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.RangoReprogramacion;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.ReglasClima;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.actividades.ActividadPostDto;
import com.tacs.backend.dtos.actividades.UbicacionDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ActividadesMapperTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);
  private static final UbicacionDto UBICACION_DTO = new UbicacionDto("Palermo", -34.58, -58.43);

  private final ActividadesMapper mapper = new ActividadesMapper();

  @Test
  void actividadPostDtoToActividadNoSeteaReglasClimaHorasAnticipacionNiRangoReprogramacion()
  {
    // Esos 3 campos no se cargan en la creacion: la actividad nace sin
    // monitoreo de clima y se configura despues via
    // PATCH /actividades/{id}/configuracion-clima.
    Usuario organizador = new Usuario("organizador", "pass", TipoRol.USER);
    ActividadPostDto dto = new ActividadPostDto(
        "Asado", "desc", TipoActividad.AIRE_LIBRE, UBICACION_DTO,
        LocalDateTime.now().plusDays(1), 2, 2, 10);

    Actividad actividad = mapper.actividadPostDtoToActividad(dto, organizador);

    assertThat(actividad.getReglasClima()).isNull();
    assertThat(actividad.getHorasAnticipacion()).isEqualTo(24);
    assertThat(actividad.getRangoReprogramacion()).isNull();
    assertThat(actividad.getOrganizador()).isEqualTo(organizador);
  }

  @Test
  void actividadToActividadDtoIncluyeReglasClimaHorasAnticipacionYRangoReprogramacion()
  {
    ReglasClima reglasClima = new ReglasClima(30, 10, 30, 20);
    RangoReprogramacion rango = new RangoReprogramacion(3, 10, 20);
    Actividad actividad = new Actividad(
        "Asado", "desc", TipoActividad.AIRE_LIBRE, UBICACION,
        LocalDateTime.now().plusDays(1), 2, LocalDateTime.now(), 2, 10, null);
    actividad.setReglasClima(reglasClima);
    actividad.setHorasAnticipacion(6);
    actividad.setRangoReprogramacion(rango);

    ActividadDto dto = mapper.actividadToActividadDto(actividad);

    assertThat(dto.reglasClima()).isEqualTo(reglasClima);
    assertThat(dto.horasAnticipacion()).isEqualTo(6);
    assertThat(dto.rangoReprogramacion()).isEqualTo(rango);
  }
}
