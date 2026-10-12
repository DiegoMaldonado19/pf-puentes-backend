package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class TokenRenovacionRepositoryTest extends PruebaIntegracion {

  @Autowired TokenRenovacionRepository repositorio;

  @Autowired UsuarioRepository usuarios;

  @Autowired TestEntityManager em;

  @Test
  void buscaUnTokenDeRenovacionPorSuHash() {
    Instant expira = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.MICROS);
    Usuario ana = usuarios.save(Cuentas.estudiante("ana@correo.com"));
    TokenRenovacion token = new TokenRenovacion();
    token.setUsuario(ana);
    token.setTokenHash(Cuentas.hash("renovacion"));
    token.setExpiraEn(expira);
    repositorio.saveAndFlush(token);
    em.clear();

    TokenRenovacion leido = repositorio.findByTokenHash(Cuentas.hash("renovacion")).orElseThrow();

    assertThat(leido.getId()).isEqualTo(token.getId());
    assertThat(leido.getExpiraEn()).isEqualTo(expira);
    assertThat(leido.getRevocadoEn()).isNull();
    assertThat(repositorio.findByTokenHash(Cuentas.hash("otro"))).isEmpty();
  }
}
