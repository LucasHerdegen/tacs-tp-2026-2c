package com.tacs.backend.persistence.repositories;

import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.persistence.entities.MedioContactoEntity;
import com.tacs.backend.persistence.entities.UsuarioEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest(properties = {
    "spring.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/tacs_test}",
    "spring.data.mongodb.auto-index-creation=true"
})
class UsuarioMongoRepositoryTest
{
  @Autowired
  private UsuarioMongoRepository usuarioMongoRepository;

  @MockitoBean
  private CacheManager cacheManager;

  @BeforeEach
  void setUp()
  {
    usuarioMongoRepository.deleteAll();
  }

  @AfterEach
  void tearDown()
  {
    usuarioMongoRepository.deleteAll();
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

  @Test
  void guardarDosUsuariosConMismoChatDeTelegramLanzaDuplicateKeyException()
  {
    UsuarioEntity primero = new UsuarioEntity();
    primero.setUsername("tg_1");
    primero.setPassword("irrelevante");
    primero.setRol(TipoRol.USER);
    primero.setMedioContacto(new MedioContactoEntity(TipoMedioContacto.TELEGRAM, "chat-duplicado"));
    UsuarioEntity guardado = usuarioMongoRepository.save(primero);
    assertThat(guardado.getId()).isNotNull();

    UsuarioEntity segundo = new UsuarioEntity();
    segundo.setUsername("tg_2");
    segundo.setPassword("irrelevante");
    segundo.setRol(TipoRol.USER);
    segundo.setMedioContacto(new MedioContactoEntity(TipoMedioContacto.TELEGRAM, "chat-duplicado"));

    assertThatThrownBy(() -> usuarioMongoRepository.save(segundo))
        .isInstanceOf(DuplicateKeyException.class);
    assertThat(usuarioMongoRepository.count()).isEqualTo(1L);
  }

  @Test
  void guardarDosUsuariosSinMedioContactoNoColisionaPorIndiceSparse()
  {
    UsuarioEntity primero = new UsuarioEntity();
    primero.setUsername("sin_tg_1");
    primero.setPassword("irrelevante");
    primero.setRol(TipoRol.USER);

    UsuarioEntity segundo = new UsuarioEntity();
    segundo.setUsername("sin_tg_2");
    segundo.setPassword("irrelevante");
    segundo.setRol(TipoRol.USER);

    usuarioMongoRepository.save(primero);
    usuarioMongoRepository.save(segundo);

    assertThat(usuarioMongoRepository.count()).isEqualTo(2L);
  }
}
