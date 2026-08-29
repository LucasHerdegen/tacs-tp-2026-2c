package com.tacs.backend.domain.votacion;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.usuario.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Votacion
{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDateTime fechaApertura;
  private boolean abierta = true;
  private LocalDateTime fechaLimite;
  private LocalDateTime fechaCierre;

  @ManyToOne
  private Actividad actividad;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Alternativa> alternativas = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Voto> votos = new ArrayList<>();

  @ManyToOne
  private Alternativa alternativaGanadora;

  private int quorumMinimo;

  public void agregarAlternativa(Alternativa alternativa)
  {
    alternativas.add(alternativa);
  }

  public void eliminarAlternativa(int numeroAlternativa)
  {
    alternativas.removeIf(a -> a.getNumeroAltenativa() == numeroAlternativa);
  }

  public void registrarVoto(Voto voto)
  {
    eliminarVoto(voto.getUsuario());
    votos.add(voto);
  }

  public void eliminarVoto(Usuario usuario)
  {
    votos.removeIf(v -> v.getUsuario().getId().equals(usuario.getId()));
  }

  public long cantidadVotos(Alternativa alternativa)
  {
    return votos.stream()
        .filter(v -> v.getAlternativa().getId().equals(alternativa.getId()))
        .count();
  }

  /**
   * Alternativa con mas votos. En caso de empate gana la que se propuso primero
   * (menor numeroAlternativa). Vacio si la votacion no tiene alternativas.
   */
  public Optional<Alternativa> alternativaMasVotada()
  {
    return alternativas.stream()
        .max(Comparator
            .comparingLong(this::cantidadVotos)
            .thenComparing(Comparator.comparingInt(Alternativa::getNumeroAltenativa).reversed()));
  }

  /**
   * Cierra la votacion dejando registrada la alternativa ganadora (o vacio si
   * no se alcanzo el quorumMinimo, en cuyo caso la actividad se cancela en vez
   * de reprogramarse).
   */
  public void cerrar(Alternativa ganadora)
  {
    this.abierta = false;
    this.fechaCierre = LocalDateTime.now();
    this.alternativaGanadora = ganadora;
  }
}
