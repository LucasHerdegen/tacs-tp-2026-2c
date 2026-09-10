package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.clima.Clima;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "alternativas")
@Getter
@Setter
@NoArgsConstructor
public class AlternativaEntity
{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private LocalDateTime fecha;

  @Embedded
  private Clima clima;

  private int numeroAltenativa;
}
