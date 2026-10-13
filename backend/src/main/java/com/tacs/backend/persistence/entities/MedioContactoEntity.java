package com.tacs.backend.persistence.entities;

import com.tacs.backend.domain.usuario.TipoMedioContacto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MedioContactoEntity
{
  private TipoMedioContacto tipo;

  // unique+sparse: un mismo chat de Telegram no puede quedar vinculado a dos
  // usuarios a la vez, pero los usuarios sin vincular (valor null) no chocan entre si.
  @Indexed(unique = true, sparse = true)
  private String valor;
}
