package com.cunoc.puentes.inspeccion.puerto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Import(ConsultaFormularioProvisional.class)
class ConsultaFormularioProvisionalTest extends PruebaIntegracion {

  private static final UUID VERSION = UUID.fromString("0199b000-0000-7000-8000-00000000f001");

  @Autowired ConsultaFormulario consulta;

  @Test
  void sinVersionActivaDevuelveVacio() {
    assertThat(consulta.versionActiva()).isEmpty();
  }

  @Test
  @Sql("/inspeccion/formulario-activo.sql")
  void leeLaVersionActivaYSusMetadatosProvisionales() {
    assertThat(consulta.versionActiva())
        .hasValueSatisfying(
            vista -> {
              assertThat(vista.id()).isEqualTo(VERSION);
              assertThat(vista.codigo()).isEqualTo("SIECA-PRUEBA");
              assertThat(vista.esquema()).containsEntry("type", "object");
            });
    assertThat(consulta.exigir(VERSION).codigo()).isEqualTo("SIECA-PRUEBA");
    assertThat(consulta.rutasPermanentes(VERSION)).contains("identificacion.tipologia");
    assertThat(consulta.rutasDeDano(VERSION)).contains("cauce.calificacion");
    assertThat(consulta.grupos(VERSION)).isEmpty();
    consulta.validarParcial(Map.of(), VERSION);
    consulta.validarCompleto(Map.of(), VERSION);
  }

  @Test
  void exigirUnaVersionInexistenteEs404() {
    UUID inexistente = UUID.fromString("0199b000-0000-7000-8000-00000000f099");
    assertThatThrownBy(() -> consulta.exigir(inexistente))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              NegocioException negocio = (NegocioException) error;
              assertThat(negocio.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(negocio.getBody().getType().toString())
                  .endsWith("/formulario-no-encontrado");
            });
  }
}
