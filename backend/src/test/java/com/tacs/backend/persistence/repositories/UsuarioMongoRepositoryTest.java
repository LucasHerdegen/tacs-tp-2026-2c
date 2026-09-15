package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.persistence.entities.MedioContactoEntity;
import com.tacs.backend.persistence.entities.UsuarioEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "security.jwt.secret=test-secret-key-with-at-least-32-bytes")
class UsuarioMongoRepositoryTest
{
  @Autowired
  private UsuarioMongoRepository usuarioMongoRepository;

  @Autowired
  private MongoTemplate mongoTemplate;

  @BeforeEach
  void setUp()
  {
    mongoTemplate.getDb().drop();
  }

  @AfterEach
  void tearDown()
  {
    mongoTemplate.getDb().drop();
  }

  @Test
  void encuentraUsuarioPorChatIdDeTelegram()
  {
    UsuarioEntity usuario = new UsuarioEntity();
    usuario.setUsername("tg_999");
    usuario.setPassword("irrelevante");
    usuario.setRol(TipoRol.USER);
    usuario.setMedioContacto(new MedioContactoEntity(TipoMedioContacto.TELEGRAM, "999"));
    usuarioMongoRepository.save(usuario);

    Optional<UsuarioEntity> encontrado = usuarioMongoRepository
        .findByMedioContacto_ValorAndMedioContacto_Tipo("999", TipoMedioContacto.TELEGRAM);

    assertThat(encontrado).isPresent();
    assertThat(encontrado.get().getUsername()).isEqualTo("tg_999");
  }

  @Test
  void noEncuentraUsuarioParaUnChatIdDesconocido()
  {
    Optional<UsuarioEntity> encontrado = usuarioMongoRepository
        .findByMedioContacto_ValorAndMedioContacto_Tipo("no-existe", TipoMedioContacto.TELEGRAM);

    assertThat(encontrado).isEmpty();
  }
}
