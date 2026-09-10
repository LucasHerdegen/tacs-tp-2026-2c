package com.tacs.backend.domain.votacion;

import com.tacs.backend.domain.usuario.Usuario;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class Voto
{
  private Long id;
  private Alternativa alternativa;
  private Usuario usuario;
}
