package com.tacs.backend.domain.votacion;

import com.tacs.backend.domain.usuario.TipoRol;
import com.tacs.backend.domain.usuario.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VotacionTest
{
  @Test
  void cantidadVotosCuentaSoloLosVotosDeEsaAlternativa()
  {
    Votacion votacion = new Votacion();
    Alternativa sabado = crearAlternativa(1L, 1);
    Alternativa domingo = crearAlternativa(2L, 2);

    votacion.registrarVoto(votoDe(crearUsuario(1L), sabado));
    votacion.registrarVoto(votoDe(crearUsuario(2L), sabado));
    votacion.registrarVoto(votoDe(crearUsuario(3L), domingo));

    assertThat(votacion.cantidadVotos(sabado)).isEqualTo(2);
    assertThat(votacion.cantidadVotos(domingo)).isEqualTo(1);
  }

  @Test
  void alternativaMasVotadaEsVaciaSiNoHayAlternativas()
  {
    Votacion votacion = new Votacion();

    assertThat(votacion.alternativaMasVotada()).isEmpty();
  }

  @Test
  void alternativaMasVotadaDevuelveLaDeMasVotos()
  {
    Votacion votacion = new Votacion();
    Alternativa sabado = crearAlternativa(1L, 1);
    Alternativa domingo = crearAlternativa(2L, 2);
    votacion.agregarAlternativa(sabado);
    votacion.agregarAlternativa(domingo);

    votacion.registrarVoto(votoDe(crearUsuario(1L), domingo));
    votacion.registrarVoto(votoDe(crearUsuario(2L), domingo));
    votacion.registrarVoto(votoDe(crearUsuario(3L), sabado));

    assertThat(votacion.alternativaMasVotada()).contains(domingo);
  }

  @Test
  void alternativaMasVotadaDesempataPorMenorNumeroDeAlternativa()
  {
    Votacion votacion = new Votacion();
    Alternativa propuestaPrimero = crearAlternativa(1L, 1);
    Alternativa propuestaDespues = crearAlternativa(2L, 2);
    votacion.agregarAlternativa(propuestaDespues); // orden de insercion invertido a proposito
    votacion.agregarAlternativa(propuestaPrimero);

    votacion.registrarVoto(votoDe(crearUsuario(1L), propuestaPrimero));
    votacion.registrarVoto(votoDe(crearUsuario(2L), propuestaDespues));

    Optional<Alternativa> ganadora = votacion.alternativaMasVotada();

    assertThat(ganadora).contains(propuestaPrimero);
  }

  @Test
  void cerrarConGanadoraDejaLaVotacionCerradaConLaAlternativaRegistrada()
  {
    Votacion votacion = new Votacion();
    Alternativa ganadora = crearAlternativa(1L, 1);

    votacion.cerrar(ganadora);

    assertThat(votacion.isAbierta()).isFalse();
    assertThat(votacion.getFechaCierre()).isNotNull();
    assertThat(votacion.getAlternativaGanadora()).isEqualTo(ganadora);
  }

  @Test
  void cerrarSinGanadoraDejaLaVotacionCerradaSinAlternativaRegistrada()
  {
    Votacion votacion = new Votacion();

    votacion.cerrar(null);

    assertThat(votacion.isAbierta()).isFalse();
    assertThat(votacion.getFechaCierre()).isNotNull();
    assertThat(votacion.getAlternativaGanadora()).isNull();
  }

  private Alternativa crearAlternativa(Long id, int numero)
  {
    Alternativa alternativa = new Alternativa();
    alternativa.setId(id);
    alternativa.setNumeroAltenativa(numero);
    alternativa.setFecha(LocalDateTime.now().plusDays(numero));
    return alternativa;
  }

  private Usuario crearUsuario(Long id)
  {
    Usuario usuario = new Usuario("usuario" + id, "password", TipoRol.USER);
    usuario.setId(id);
    return usuario;
  }

  private Voto votoDe(Usuario usuario, Alternativa alternativa)
  {
    Voto voto = new Voto();
    voto.setUsuario(usuario);
    voto.setAlternativa(alternativa);
    return voto;
  }
}