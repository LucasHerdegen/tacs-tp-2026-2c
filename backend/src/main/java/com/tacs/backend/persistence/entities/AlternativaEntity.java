package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.clima.Clima;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AlternativaEntity
{
    private String id;

    private LocalDateTime fecha;

  private Clima clima;

  private int numeroAltenativa;
}
