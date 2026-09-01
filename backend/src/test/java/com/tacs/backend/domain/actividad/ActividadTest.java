package com.tacs.backend.domain.actividad;

import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ActividadTest
{
  private static final Ubicacion UBICACION = new Ubicacion("Palermo", -34.58, -58.43);

  @Test
  void reprogramarReseteaElRecordatorioParaQueSeAviseDeLaNuevaFecha()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusDays(1));
    actividad.marcarRecordatorioEnviado();

    actividad.reprogramar(LocalDateTime.now().plusDays(5));

    assertThat(actividad.isRecordatorioEnviado()).isFalse();
  }

  @Test
  void reprogramarDejaRegistradoElCambioDeFecha()
  {
    LocalDateTime fechaOriginal = LocalDateTime.now().plusDays(1);
    LocalDateTime fechaNueva = LocalDateTime.now().plusDays(5);

    Actividad actividad = crearActividad(fechaOriginal);

    actividad.reprogramar(fechaNueva);

    assertThat(actividad.getFechaRealizacion()).isEqualTo(fechaNueva);
    assertThat(actividad.getCambiosFecha()).hasSize(1);
    assertThat(actividad.getCambiosFecha().get(0).getFechaAntigua()).isEqualTo(fechaOriginal);
    assertThat(actividad.getCambiosFecha().get(0).getFechaNueva()).isEqualTo(fechaNueva);
  }

  @Test
  void reprogramarDejaLaActividadEnEstadoReprogramada()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusDays(1));

    actividad.reprogramar(LocalDateTime.now().plusDays(5));

    assertThat(actividad.getEstado()).isEqualTo(TipoEstadoActividad.REPROGRAMADA);
  }

  @Test
  void elOrganizadorQuedaComoParticipanteAlCrearLaActividad()
  {
    Usuario organizador = crearUsuarioConId(999L);

    Actividad actividad = new Actividad(
        "Asado en el parque", "Actividad de prueba", TipoActividad.AIRE_LIBRE, UBICACION,
        LocalDateTime.now().plusDays(1), 2, LocalDateTime.now(), 2, 10, organizador);

    assertThat(actividad.getParticipantes()).containsExactly(organizador);
  }

  @Test
  void unaActividadRecienCreadaNoTieneElRecordatorioEnviado()
  {
    Actividad actividad = crearActividad(LocalDateTime.now().plusDays(1));

    assertThat(actividad.isRecordatorioEnviado()).isFalse();
  }

  /* ==================== Auxiliares ==================== */
  private Actividad crearActividad(LocalDateTime fechaRealizacion)
  {
    Actividad actividad = new Actividad(
        "Asado en el parque",
        "Actividad de prueba",
        TipoActividad.AIRE_LIBRE,
        UBICACION,
        fechaRealizacion,
        2,
        LocalDateTime.now(),
        2,
        10,
        crearUsuarioConId(999L));

    actividad.setEstado(TipoEstadoActividad.PROPUESTA);

    return actividad;
  }

  private Usuario crearUsuarioConId(Long id)
  {
    Usuario usuario = new Usuario("usuario" + id, "password", TipoRol.USER);
    usuario.setId(id);
    return usuario;
  }
}
