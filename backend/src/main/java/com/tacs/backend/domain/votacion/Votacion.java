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
import java.util.List;

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
  private LocalDateTime fechaCierre;
  private boolean abierta = true;


  @ManyToOne
  private Actividad actividad;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Alternativa> alternativas = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Voto> votos = new ArrayList<>();

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
}
