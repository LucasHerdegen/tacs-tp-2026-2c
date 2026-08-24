package com.tacs.backend.domain.actividad;

import com.tacs.backend.exceptions.EstadoInvalidoException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class EstadoActividad
{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private TipoEstadoActividad tipo;

  @ManyToMany
  @JoinTable(
      name = "transiciones_estado",
      joinColumns = @JoinColumn(name = "estado_origen_id"),
      inverseJoinColumns = @JoinColumn(name = "estado_destino_id")
  )
  private List<EstadoActividad> posiblesEstados;

  public void cambiarEstado(Actividad actividad, TipoEstadoActividad nuevoTipo)
  {
    EstadoActividad nuevoEstado = posiblesEstados.stream()
        .filter(e -> e.getTipo().equals(nuevoTipo))
        .findFirst()
        .orElseThrow(() -> new EstadoInvalidoException("No se puede pasar de " + tipo + " a " + nuevoTipo));

    actividad.setEstado(nuevoEstado);
  }
}
