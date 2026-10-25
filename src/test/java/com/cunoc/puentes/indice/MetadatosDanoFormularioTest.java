package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

/** Esquema JSONB de Flyway y versiones reales; no usa el archivo como sustituto de la base. */
@SpringBootTest
@Transactional
@Sql("/indice/servicio-pesos.sql")
class MetadatosDanoFormularioTest extends PruebaIntegracion {
  private static final UUID SIECA = UUID.fromString("0192b000-0000-7000-8000-000000000001");
  private static final UUID A = UUID.fromString("019a0018-0000-7000-8000-000000000001");
  private static final UUID B = UUID.fromString("019a0018-0000-7000-8000-000000000002");
  private static final UUID ADMIN = UUID.fromString("019a0018-0000-7000-8000-000000000003");
  @Autowired MetadatosDanoFormulario metadatos;
  @Autowired ConfiguracionPesosService pesos;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entidades;

  @AfterEach
  void limpiarSesion() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void extraeLosDiecinueveCamposDeLaSemillaRealSinConfundirRutaYReferencia() {
    var elementos = metadatos.obtener(SIECA);
    assertThat(elementos.formularioVersionId()).isEqualTo(SIECA);
    assertThat(elementos.referencias())
        .hasSize(19)
        .contains(
            "subestructura.estribo_entrada.socavacion",
            "subestructura.cimentacion.asentamiento",
            "superestructura.vigas.ausencia_elemento",
            "caracteristicas_cauce.obstruccion")
        .doesNotContain("subestructura.estribo_entrada_socavacion", "identificacion.codigo");
  }

  @Test
  void noMezclaVersionesNiGuardaEnCacheBorradoresEditables() throws Exception {
    esquema(A, "primero");
    esquema(B, "segundo");
    assertThat(metadatos.obtener(A).referencias()).containsExactly("primero");
    assertThat(metadatos.obtener(B).referencias()).containsExactly("segundo");
    esquema(A, "actualizado");
    assertThat(metadatos.obtener(A).referencias()).containsExactly("actualizado");
    assertThat(metadatos.obtener(B).referencias()).containsExactly("segundo");
  }

  @Test
  void versionesHistoricasSeLeenPorSuIdSinSeleccionarLaActiva() throws Exception {
    esquema(A, "historico");
    jdbc.update("UPDATE formulario_version SET estado='RETIRADA',publicada_en=now() WHERE id=?", A);
    assertThat(metadatos.obtener(A).referencias()).containsExactly("historico");
  }

  @Test
  void rechazaVersionAusenteONulaYEsquemasPersistidosNoInterpretables() {
    assertThatThrownBy(
            () -> metadatos.obtener(UUID.fromString("019a0024-0000-7000-8000-000000000099")))
        .isInstanceOfSatisfying(
            NegocioException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    assertThatThrownBy(() -> metadatos.obtener(null)).isInstanceOf(IllegalArgumentException.class);
    jdbc.update("UPDATE formulario_version SET esquema='[]'::jsonb WHERE id=?", A);
    assertThatThrownBy(() -> metadatos.obtener(A))
        .isInstanceOfSatisfying(
            NegocioException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
  }

  @Test
  void lecturaSinEscriturasNiAuditoriasDeMutacion() {
    var antes =
        jdbc.queryForMap(
            "SELECT esquema,estado,actualizado_en FROM formulario_version WHERE id=?", SIECA);
    Long auditorias = jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class);
    metadatos.obtener(SIECA);
    assertThat(
            jdbc.queryForMap(
                "SELECT esquema,estado,actualizado_en FROM formulario_version WHERE id=?", SIECA))
        .isEqualTo(antes);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class))
        .isEqualTo(auditorias);
  }

  @Test
  void servicioExistenteAceptaReferenciasRealesYRechazaUnaRutaInventada() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                ADMIN.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))));
    var elementos = metadatos.obtener(SIECA);
    UUID creado =
        pesos.crear(
            new JuegoPesos(
                SIECA,
                null,
                List.of(
                    new PesoConfigurado(
                        "subestructura.estribo_entrada.socavacion", BigDecimal.ONE))),
            elementos);
    entidades.flush();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM peso_elemento WHERE version_pesos_id=?", Long.class, creado))
        .isEqualTo(1L);
    assertThatThrownBy(
            () ->
                pesos.crear(
                    new JuegoPesos(
                        SIECA,
                        null,
                        List.of(
                            new PesoConfigurado(
                                "subestructura.estribo_entrada_socavacion", BigDecimal.ONE))),
                    elementos))
        .isInstanceOfSatisfying(
            NegocioException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
  }

  private void esquema(UUID id, String referencia) throws Exception {
    Map<String, Object> esquema =
        ExtractorElementosDeDanoTest.objeto(
            Map.of("nombre_fisico", ExtractorElementosDeDanoTest.dano(referencia)));
    jdbc.update(
        "UPDATE formulario_version SET esquema=?::jsonb WHERE id=?",
        new ObjectMapper().writeValueAsString(esquema),
        id);
  }
}
