package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Import(ValidacionOrigenOrden.class)
@Sql("/mantenimiento/ordenes.sql")
class ValidacionOrigenOrdenTest extends PruebaIntegracion {

  private static final UUID PUENTE = UUID.fromString("019a0022-0000-7000-8000-000000000001");
  private static final UUID OTRO_PUENTE = UUID.fromString("019a0022-0000-7000-8000-000000000002");
  private static final UUID INSPECCION = UUID.fromString("019a0022-0000-7000-8000-000000000005");
  private static final UUID INEXISTENTE = UUID.fromString("019a0022-0000-7000-8000-000000000099");

  @Autowired ValidacionOrigenOrden validacion;
  @Autowired JdbcTemplate jdbc;

  @Test
  void aceptaUnPuenteExistenteConInspeccionDelMismoPuente() {
    assertThatNoException().isThrownBy(() -> validacion.validar(PUENTE, INSPECCION));
  }

  @Test
  void laInspeccionDeOrigenEsOpcional() {
    assertThatNoException().isThrownBy(() -> validacion.validar(OTRO_PUENTE, null));
  }

  @Test
  void unPuenteAusenteNoSeInterpretaComoOtroOrigenValido() {
    assertThatIllegalArgumentException().isThrownBy(() -> validacion.validar(null, INSPECCION));
  }

  @Test
  void rechazaUnPuenteInexistenteAunqueNoSeIndiqueInspeccion() {
    comprobarError(
        HttpStatus.NOT_FOUND, "puente-no-encontrado", () -> validacion.validar(INEXISTENTE, null));
  }

  @Test
  void rechazaUnaInspeccionInexistente() {
    comprobarError(
        HttpStatus.NOT_FOUND,
        "inspeccion-no-encontrada",
        () -> validacion.validar(PUENTE, INEXISTENTE));
  }

  @Test
  void rechazaElOrigenDeOtroPuenteAunqueAmbasClavesExistan() {
    comprobarError(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "origen-de-orden-incompatible",
        () -> validacion.validar(OTRO_PUENTE, INSPECCION));
  }

  @Test
  void validarNoCreaOrdenesNiModificaLaInspeccion() {
    Long ordenes = jdbc.queryForObject("SELECT count(*) FROM orden_mantenimiento", Long.class);

    validacion.validar(PUENTE, INSPECCION);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM orden_mantenimiento", Long.class))
        .isEqualTo(ordenes);
    assertThat(
            jdbc.queryForObject(
                "SELECT puente_id FROM inspeccion WHERE id = ?", UUID.class, INSPECCION))
        .isEqualTo(PUENTE);
  }

  private static void comprobarError(HttpStatus estado, String tipo, Runnable operacion) {
    assertThatThrownBy(operacion::run)
        .isInstanceOfSatisfying(
            NegocioException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(estado);
              assertThat(error.getBody().getType().toString()).endsWith("/" + tipo);
              assertThat(error.getBody().getDetail()).isNotBlank();
            });
  }
}
