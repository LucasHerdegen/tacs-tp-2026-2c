package com.tacs.backend.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "votos")
@Getter
@Setter
@NoArgsConstructor
public class VotoEntity
{
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "alternativa_id")
  private AlternativaEntity alternativa;

  @ManyToOne(optional = false)
  @JoinColumn(name = "usuario_id")
  private UsuarioEntity usuario;
}
