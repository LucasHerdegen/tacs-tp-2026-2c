package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.persistence.entities.MedioContactoEntity;
import com.tacs.backend.persistence.entities.UsuarioEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioMongoRepositoryTest
{
  @Mock
  private UsuarioMongoRepository usuarioMongoRepository;

  @Test
  void encuentraUsuarioPorChatIdDeTelegram()
  {
    UsuarioEntity usuario = new UsuarioEntity();
    usuario.setUsername("tg_999");
    usuario.setPassword("irrelevante");
    usuario.setRol(TipoRol.USER);
    usuario.setMedioContacto(new MedioContactoEntity(TipoMedioContacto.TELEGRAM, "999"));

    when(usuarioMongoRepository.findByMedioContacto_ValorAndMedioContacto_Tipo("999", TipoMedioContacto.TELEGRAM))
        .thenReturn(Optional.of(usuario));

    Optional<UsuarioEntity> encontrado = usuarioMongoRepository
        .findByMedioContacto_ValorAndMedioContacto_Tipo("999", TipoMedioContacto.TELEGRAM);

    assertThat(encontrado).isPresent();
    assertThat(encontrado.get().getUsername()).isEqualTo("tg_999");
  }

  @Test
  void noEncuentraUsuarioParaUnChatIdDesconocido()
  {
    when(usuarioMongoRepository.findByMedioContacto_ValorAndMedioContacto_Tipo("no-existe", TipoMedioContacto.TELEGRAM))
        .thenReturn(Optional.empty());

    Optional<UsuarioEntity> encontrado = usuarioMongoRepository
        .findByMedioContacto_ValorAndMedioContacto_Tipo("no-existe", TipoMedioContacto.TELEGRAM);

    assertThat(encontrado).isEmpty();
  }
}
