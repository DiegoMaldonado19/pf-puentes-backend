package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class TokenVerificacionRepositoryTest extends PruebaIntegracion {

  private static final TipoTokenVerificacion CORREO = TipoTokenVerificacion.VERIFICACION_CORREO;

  @Autowired TokenVerificacionRepository repositorio;

  @Autowired UsuarioRepository usuarios;

  @Autowired TestEntityManager em;

  // creado_en lo fija Hibernate con la hora real y la base exige expira_en > creado_en, así que un
  // token no puede nacer vencido: se consulta con un "ahora" posterior a su vencimiento.
  @Test
  void consultaSoloLosTokensVigentesDelUsuarioYElTipo() {
    Instant emision = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Usuario ana = usuarios.save(Cuentas.estudiante("ana@correo.com"));
    Usuario luis = usuarios.save(Cuentas.estudiante("luis@correo.com"));
    TokenVerificacion vencido =
        Cuentas.token(ana, CORREO, "vencido", emision.plus(Duration.ofMinutes(10)));
    TokenVerificacion usado =
        Cuentas.token(ana, CORREO, "usado", emision.plus(Duration.ofHours(1)));
    usado.setUsadoEn(emision);
    TokenVerificacion vigente =
        Cuentas.token(ana, CORREO, "vigente", emision.plus(Duration.ofHours(1)));
    TokenVerificacion deOtroTipo =
        Cuentas.token(
            ana,
            TipoTokenVerificacion.RECUPERACION_CONTRASENA,
            "otro-tipo",
            emision.plus(Duration.ofHours(1)));
    TokenVerificacion deOtroUsuario =
        Cuentas.token(luis, CORREO, "otro-usuario", emision.plus(Duration.ofHours(1)));
    repositorio.saveAllAndFlush(List.of(vencido, usado, vigente, deOtroTipo, deOtroUsuario));
    em.clear();

    Instant ahora = emision.plus(Duration.ofMinutes(30));

    assertThat(repositorio.buscarVigentes(ana.getId(), CORREO, ahora))
        .extracting(TokenVerificacion::getTokenHash)
        .containsExactly(vigente.getTokenHash());
  }

  @Test
  void buscaUnTokenPorSuHash() {
    Instant expira = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
    Usuario ana = usuarios.save(Cuentas.estudiante("ana@correo.com"));
    TokenVerificacion token =
        repositorio.saveAndFlush(Cuentas.token(ana, CORREO, "conocido", expira));
    em.clear();

    TokenVerificacion leido = repositorio.findByTokenHash(Cuentas.hash("conocido")).orElseThrow();

    assertThat(leido.getId()).isEqualTo(token.getId());
    assertThat(leido.getTipo()).isEqualTo(CORREO);
    assertThat(leido.getExpiraEn()).isEqualTo(expira);
    assertThat(leido.getUsadoEn()).isNull();
    assertThat(repositorio.findByTokenHash(Cuentas.hash("desconocido"))).isEmpty();
  }

  // DT-SEC-12: el valor del token nunca se consulta ni se guarda; solo su hash
  @Test
  void noExisteNingunaBusquedaPorElValorSinHash() {
    assertThat(Arrays.stream(TokenVerificacionRepository.class.getDeclaredMethods()))
        .extracting(metodo -> metodo.getName().toLowerCase())
        .filteredOn(nombre -> nombre.contains("token"))
        .allMatch(nombre -> nombre.contains("hash"));
    assertThat(Arrays.stream(TokenVerificacion.class.getDeclaredFields()))
        .extracting(campo -> campo.getName().toLowerCase())
        .filteredOn(nombre -> nombre.contains("token"))
        .containsExactly("tokenhash");
  }

  @Test
  void laBaseRechazaUnTokenQueNaceVencido() {
    Usuario ana = usuarios.save(Cuentas.estudiante("ana@correo.com"));
    TokenVerificacion vencido =
        Cuentas.token(ana, CORREO, "vencido", Instant.now().minus(Duration.ofHours(1)));

    assertThatThrownBy(() -> repositorio.saveAndFlush(vencido))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
