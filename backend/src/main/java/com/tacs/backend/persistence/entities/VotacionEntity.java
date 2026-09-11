package com.tacs.backend.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "votaciones")
@Getter
@Setter
@NoArgsConstructor
public class VotacionEntity
{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDateTime fechaApertura;
  private LocalDateTime fechaCierre;
  private boolean abierta = true;
  private LocalDateTime fechaLimite;

  @ManyToOne(optional = false)
  @JoinColumn(name = "actividad_id")
  private ActividadEntity actividad;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "votacion_id")
  private List<AlternativaEntity> alternativas = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "votacion_id_votos")
  private List<VotoEntity> votos = new ArrayList<>();

  @ManyToOne
  private AlternativaEntity alternativaGanadora;

  private int quorumMinimo;
}
