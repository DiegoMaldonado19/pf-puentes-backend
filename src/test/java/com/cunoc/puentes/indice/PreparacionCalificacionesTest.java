package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/** Adaptación interna contra Flyway/PostgreSQL, sin fabricar publicación P5 ni usar la BD local. */
@SpringBootTest
@Transactional
@Sql({"/indice/servicio-pesos.sql", "/indice/creacion-juegos.sql"})
class PreparacionCalificacionesTest extends PruebaIntegracion {
  private static final UUID FORM = UUID.fromString("019a0018-0000-7000-8000-000000000001");
  private static final UUID OTRA = UUID.fromString("019a0018-0000-7000-8000-000000000002");
  private static final UUID JUEGO = UUID.fromString("019a0040-0000-7000-8000-000000000011");
  private static final UUID SIECA = UUID.fromString("0192b000-0000-7000-8000-000000000001");
  @Autowired PreparacionCalificacionesService servicio;
  @Autowired JdbcTemplate jdbc;

  @Test
  void preparaDesdeVersionRetiradaSinEscriturasYConJuegoExplicito() {
    completar();
    var antes = estado();
    var datos = Map.<String, Object>of("a", 2, "b", 4);
    var resultado = servicio.preparar(FORM, JUEGO, datos);
    assertThat(resultado)
        .containsExactly(
            new CalificacionElemento(2, new BigDecimal("1.000")),
            new CalificacionElemento(4, new BigDecimal("3.000")));
    // Usa el núcleo existente solo para demostrar compatibilidad de las entradas; no publica.
    assertThat(new CalculadoraIndiceCondicion().calcular(resultado)).isEqualByComparingTo("12.5");
    assertThat(estado()).isEqualTo(antes);
    assertThat(datos).containsExactlyInAnyOrderEntriesOf(Map.of("a", 2, "b", 4));
  }

  @Test
  void adaptaLosDiecinueveCamposDeLaSemillaPersistidaConSusRutasReales() {
    jdbc.update("UPDATE version_pesos SET formulario_version_id=? WHERE id=?", SIECA, JUEGO);
    jdbc.update("DELETE FROM peso_elemento WHERE version_pesos_id=?", JUEGO);
    jdbc.update(
        """
        INSERT INTO peso_elemento(id,version_pesos_id,elemento_ref,peso)
        SELECT gen_random_uuid(), ?, campo->>'x-elemento-ref',
          CASE WHEN campo->>'x-elemento-ref'='caracteristicas_cauce.obstruccion' THEN 3 ELSE 1 END
        FROM formulario_version f,
          jsonb_each(f.esquema->'properties') seccion,
          jsonb_each(seccion.value->'properties') propiedad(clave,campo)
        WHERE f.id=? AND campo->>'x-dano'='true'
        """,
        JUEGO,
        SIECA);
    var antes = estado();
    var resultado = servicio.preparar(SIECA, JUEGO, respuestasSieca());
    assertThat(resultado).hasSize(19);
    assertThat(resultado.getFirst())
        .isEqualTo(new CalificacionElemento(0, new BigDecimal("1.000")));
    assertThat(resultado).contains(new CalificacionElemento(4, new BigDecimal("3.000")));
    assertThat(new CalculadoraIndiceCondicion().calcular(resultado))
        .isCloseTo(
            new BigDecimal("85.71428571428571428571428571428571"),
            within(new BigDecimal("0.00000000000000000000000000000001")));
    assertThat(estado()).isEqualTo(antes);
  }

  @Test
  void noSeleccionaElJuegoMasRecienteNiCambiaElOriginalAlAgregarOtro() {
    completar();
    UUID nuevo = UUID.fromString("019a0025-0000-7000-8000-000000000012");
    jdbc.update("INSERT INTO version_pesos(id,formulario_version_id) VALUES (?,?)", nuevo, FORM);
    jdbc.update(
        """
        INSERT INTO peso_elemento(id,version_pesos_id,elemento_ref,peso)
        SELECT gen_random_uuid(),?,elemento_ref,9 FROM peso_elemento WHERE version_pesos_id=?
        """,
        nuevo,
        JUEGO);
    assertThat(servicio.preparar(FORM, JUEGO, Map.of("a", 0, "b", 4)))
        .containsExactly(
            new CalificacionElemento(0, new BigDecimal("1.000")),
            new CalificacionElemento(4, new BigDecimal("3.000")));
    assertThat(servicio.preparar(FORM, nuevo, Map.of("a", 0, "b", 4)))
        .containsExactly(
            new CalificacionElemento(0, new BigDecimal("9.000")),
            new CalificacionElemento(4, new BigDecimal("9.000")));
  }

  @Test
  void cargaTodosLosPesosSinAplicarLaPaginacionDeLaApiAdministrativa() throws Exception {
    var propiedades = new HashMap<String, Object>();
    var datos = new HashMap<String, Object>();
    jdbc.update("DELETE FROM peso_elemento WHERE version_pesos_id=?", JUEGO);
    for (int i = 0; i < 101; i++) {
      String referencia = "elemento." + i;
      propiedades.put("campo_" + i, ExtractorElementosDeDanoTest.dano(referencia));
      datos.put("campo_" + i, i % 5);
      jdbc.update(
          "INSERT INTO peso_elemento(id,version_pesos_id,elemento_ref,peso) VALUES (gen_random_uuid(),?,?,1)",
          JUEGO,
          referencia);
    }
    jdbc.update(
        "UPDATE formulario_version SET esquema=?::jsonb WHERE id=?",
        new ObjectMapper().writeValueAsString(ExtractorElementosDeDanoTest.objeto(propiedades)),
        FORM);
    assertThat(servicio.preparar(FORM, JUEGO, datos)).hasSize(101);
  }

  @Test
  void rechazaJuegoHistoricoParcialOVacioSinCalcularConSubconjunto() {
    falla(FORM, JUEGO, Map.of("a", 2, "b", 4), 422, "juego-pesos-incompatible");
    jdbc.update("DELETE FROM peso_elemento WHERE version_pesos_id=?", JUEGO);
    falla(FORM, JUEGO, Map.of("a", 2, "b", 4), 422, "juego-pesos-incompatible");
  }

  @Test
  void detectaCambioDeBorradorSinCacheNiMezclarLasReferenciasAnteriores() {
    completar();
    jdbc.update(
        "UPDATE formulario_version SET estado='BORRADOR',publicada_en=null WHERE id=?", FORM);
    assertThat(servicio.preparar(FORM, JUEGO, Map.of("a", 2, "b", 4))).hasSize(2);
    jdbc.update(
        "UPDATE formulario_version SET esquema=replace(esquema::text,'elemento.b','nuevo')::jsonb WHERE id=?",
        FORM);
    falla(FORM, JUEGO, Map.of("a", 2, "b", 4), 422, "juego-pesos-incompatible");
  }

  @Test
  void propagaErroresDelEsquemaYRechazaAusenciasConParametrosObligatorios() {
    UUID ausente = UUID.fromString("019a0025-0000-7000-8000-000000000099");
    falla(ausente, JUEGO, Map.of(), 404, "formulario-no-encontrado");
    falla(FORM, ausente, Map.of(), 404, "juego-pesos-no-encontrado");
    falla(OTRA, JUEGO, Map.of(), 422, "juego-pesos-de-otra-version");
    jdbc.update("UPDATE formulario_version SET esquema='[]'::jsonb WHERE id=?", FORM);
    falla(FORM, JUEGO, Map.of(), 422, "metadatos-de-dano-invalidos");
    assertThatThrownBy(() -> servicio.preparar(null, JUEGO, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.preparar(FORM, null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> servicio.preparar(FORM, JUEGO, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void datosIncompletosNoSeConviertenEnSanosNiModificanInspecciones() {
    completar();
    var antes = estado();
    falla(FORM, JUEGO, Map.of("a", 0), 422, "respuesta-dano-invalida");
    assertThat(estado()).isEqualTo(antes);
  }

  private void completar() {
    jdbc.update(
        "INSERT INTO peso_elemento(id,version_pesos_id,elemento_ref,peso) VALUES (gen_random_uuid(),?,'elemento.b',3)",
        JUEGO);
  }

  private List<?> estado() {
    return List.of(
        jdbc.queryForList("SELECT * FROM inspeccion ORDER BY id"),
        jdbc.queryForList("SELECT * FROM puente ORDER BY id"),
        jdbc.queryForList("SELECT * FROM formulario_version ORDER BY id"),
        jdbc.queryForList("SELECT * FROM version_pesos ORDER BY id"),
        jdbc.queryForList("SELECT * FROM peso_elemento ORDER BY id"),
        jdbc.queryForList("SELECT * FROM auditoria ORDER BY id"));
  }

  private void falla(UUID form, UUID juego, Map<String, Object> datos, int estado, String tipo) {
    assertThatThrownBy(() -> servicio.preparar(form, juego, datos))
        .isInstanceOfSatisfying(
            NegocioException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(estado);
              assertThat(e.getBody().getType().getPath()).endsWith(tipo);
            });
  }

  private static Map<String, Object> respuestasSieca() {
    return Map.of(
        "subestructura",
            Map.of(
                "estribo_entrada_grietas",
                0,
                "estribo_entrada_socavacion",
                0,
                "estribo_salida_grietas",
                0,
                "estribo_salida_socavacion",
                0,
                "pilas_grietas",
                0,
                "pilas_socavacion",
                0,
                "asentamiento_estructural",
                0),
        "superestructura",
            Map.of(
                "vigas_deformacion",
                0,
                "vigas_perdida_seccion",
                0,
                "vigas_ausencia_elemento",
                0,
                "losa_agrietamiento",
                0,
                "juntas_dilatacion",
                0,
                "aparatos_apoyo",
                0),
        "elementos_no_estructurales",
            Map.of("barandas_danos", 0, "aceras_danos", 0, "drenajes_danos", 0),
        "caracteristicas_cauce",
            Map.of("obstruccion_cauce", 4, "alineamiento_flujo", 0, "proteccion_margenes", 0),
        "datos_generales", Map.of("longitud_total", 100, "numero_tramos", 4),
        "tramos", List.of(Map.of("numero_tramo", 1, "longitud", 100)));
  }
}
