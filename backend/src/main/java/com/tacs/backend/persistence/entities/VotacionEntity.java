package com.tacs.backend.persistence.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "votaciones")
@Getter
@Setter
@NoArgsConstructor
public class VotacionEntity
{
  @Id
  private String id;

  private LocalDateTime fechaApertura;
  private LocalDateTime fechaCierre;
  private boolean abierta = true;
  private LocalDateTime fechaLimite;

  @DocumentReference
  private ActividadEntity actividad;

  private List<AlternativaEntity> alternativas = new ArrayList<>();

  private List<VotoEntity> votos = new ArrayList<>();

  private AlternativaEntity alternativaGanadora;

  private int quorumMinimo;
}
