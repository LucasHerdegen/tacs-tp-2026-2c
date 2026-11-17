package com.tacs.backend.domain.actividad;

import com.tacs.backend.exceptions.EstadoInvalidoException;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EstadoActividad
{

  private TipoEstadoActividad tipo;
  private List<TipoEstadoActividad> posiblesEstados;

  public EstadoActividad(TipoEstadoActividad tipo, List<TipoEstadoActividad> posiblesEstados)
  {
    this.tipo = tipo;
    this.posiblesEstados = posiblesEstados;
  }

  public void validarCambioEstado(TipoEstadoActividad nuevoTipo)
  {
    if (!posiblesEstados.contains(nuevoTipo))
      throw new EstadoInvalidoException("No se puede pasar de " + tipo + " a " + nuevoTipo);
  }

  public void cambiarEstado(Actividad actividad, TipoEstadoActividad nuevoTipo)
  {
    validarCambioEstado(nuevoTipo);

    actividad.setEstado(nuevoTipo);
  }
}
