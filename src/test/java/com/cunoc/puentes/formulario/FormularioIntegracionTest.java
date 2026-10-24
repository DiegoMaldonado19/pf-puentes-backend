package com.cunoc.puentes.formulario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.formulario.dto.CrearFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.FormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PublicarFormularioVersionDTO;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueba de integración real con Testcontainers (PostgreSQL 17 + PostGIS y MinIO reales)
 * (DT-CAL-02). Verifica persistencia JSONB, migración Flyway semilla V1.0.0 y el índice único
 * parcial condicional.
 */
@SpringBootTest
class FormularioIntegracionTest extends PruebaIntegracion {

  @Autowired private FormularioVersionService servicio;
  @Autowired private FormularioVersionRepository repository;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void limpiarPruebas() {
    // Eliminar versiones creadas por las pruebas para dejar solo la semilla original
    jdbc.update("DELETE FROM formulario_version WHERE codigo LIKE 'V%-TEST%'");
  }

  @Test
  @DisplayName(
      "Migración Flyway inicial carga la versión semilla V1.0.0 oficial en estado ACTIVA (RN-FRM-04)")
  void migracionSemilla_formularioSiecaV1Activo() {
    Optional<FormularioVersion> activa = repository.findByEstado(EstadoFormulario.ACTIVA);

    assertThat(activa).isPresent();
    FormularioVersion version = activa.get();
    assertThat(version.getCodigo()).isEqualTo("V1.0.0");
    assertThat(version.getEstado()).isEqualTo(EstadoFormulario.ACTIVA);
    assertThat(version.getPublicadaEn()).isNotNull();

    // Comprobar persistencia real del JSONB con las 8 secciones normativas SIECA
    Map<String, Object> esquema = version.getEsquema();
    assertThat(esquema).isNotNull();
    @SuppressWarnings("unchecked")
    Map<String, Object> properties = (Map<String, Object>) esquema.get("properties");
    assertThat(properties)
        .containsKeys(
            "identificacion",
            "datos_generales",
            "tramos",
            "subestructura",
            "superestructura",
            "elementos_no_estructurales",
            "caracteristicas_cauce",
            "otros");
  }

  @Test
  @DisplayName(
      "Ciclo completo de creación y publicación inmutable sobre base de datos real (RN-FRM-03)")
  void crearYPublicar_transicionAtomicaEnBaseDeDatos() {
    Map<String, Object> esquemaNuevo =
        Map.of(
            "title",
            "Esquema V2 Test",
            "type",
            "object",
            "properties",
            Map.of("campo", Map.of("type", "string")));

    // 1. Crear borrador
    CrearFormularioVersionDTO crearDto = new CrearFormularioVersionDTO("V2.0.0-TEST", esquemaNuevo);
    FormularioVersionDTO borrador = servicio.crearBorrador(crearDto);

    assertThat(borrador.codigo()).isEqualTo("V2.0.0-TEST");
    assertThat(borrador.estado()).isEqualTo(EstadoFormulario.BORRADOR);

    // 2. Publicar con mapeo de equivalencias (RN-FRM-06)
    Map<String, Object> equivalencias = Map.of("identificacion.antiguo", "identificacion.nuevo");
    PublicarFormularioVersionDTO publicarDto = new PublicarFormularioVersionDTO(equivalencias);
    FormularioVersionDTO publicada = servicio.publicar(borrador.id(), publicarDto);

    assertThat(publicada.estado()).isEqualTo(EstadoFormulario.ACTIVA);
    assertThat(publicada.mapeo()).containsEntry("identificacion.antiguo", "identificacion.nuevo");

    // 3. Verificar que la versión previa pasó a RETIRADA
    Optional<FormularioVersion> previa = repository.findByCodigo("V1.0.0");
    assertThat(previa).isPresent();
    assertThat(previa.get().getEstado()).isEqualTo(EstadoFormulario.RETIRADA);
  }

  @Test
  @DisplayName(
      "Índice parcial condicional formulario_version_activa_uk impide dos activas simultáneas")
  void indiceUnicoParcial_impideDosVersionesActivas() {
    FormularioVersion duplicada =
        new FormularioVersion("V-DUPLICADA-TEST", Map.of("type", "object"));
    duplicada.setEstado(EstadoFormulario.ACTIVA);

    assertThatThrownBy(() -> repository.saveAndFlush(duplicada))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
