package com.cunoc.puentes.inspeccion.puerto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Import(PuertosInspeccionConfig.class)
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
    assertThat(consulta.grupos(VERSION))
        .anySatisfy(grupo -> assertThat(grupo.id()).isEqualTo("subestructura.estribo_entrada"));
    consulta.validarParcial(Map.of(), VERSION);
    assertThatThrownBy(() -> consulta.validarCompleto(Map.of(), VERSION))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              NegocioException negocio = (NegocioException) error;
              assertThat(negocio.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
              assertThat(errores(negocio))
                  .extracting(ErrorCampo::campo)
                  .contains("identificacion.tipologia", "cauce.calificacion");
            });
    Map<String, Object> completos = completos(VERSION);
    consulta.validarCompleto(completos, VERSION);
    RutasDatos.escribir(completos, "cauce.calificacion", 9);
    assertThatThrownBy(() -> consulta.validarCompleto(completos, VERSION))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error ->
                assertThat(errores((NegocioException) error))
                    .extracting(ErrorCampo::campo)
                    .contains("cauce.calificacion"));
    assertThatThrownBy(
            () ->
                consulta.validarParcial(
                    Map.of("identificacion", Map.of("numero_tramos", "tres")), VERSION))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            error -> {
              NegocioException negocio = (NegocioException) error;
              assertThat(negocio.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            });
  }

  private Map<String, Object> completos(UUID version) {
    Map<String, Object> datos = new LinkedHashMap<>();
    for (String ruta : consulta.rutasPermanentes(version)) {
      String cola = ruta.substring(ruta.lastIndexOf('.') + 1);
      Object valor =
          switch (cola) {
            case "numero_tramos", "anio_construccion" -> 2;
            default -> cola.endsWith("_m") ? 10 : "x";
          };
      RutasDatos.escribir(datos, ruta, valor);
    }
    for (String ruta : consulta.rutasDeDano(version)) {
      RutasDatos.escribir(datos, ruta, 1);
    }
    return datos;
  }

  @SuppressWarnings("unchecked")
  private static List<ErrorCampo> errores(NegocioException error) {
    Object crudo =
        error.getBody().getProperties() == null
            ? null
            : error.getBody().getProperties().get("errores");
    return crudo instanceof List<?> lista ? (List<ErrorCampo>) lista : List.of();
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
