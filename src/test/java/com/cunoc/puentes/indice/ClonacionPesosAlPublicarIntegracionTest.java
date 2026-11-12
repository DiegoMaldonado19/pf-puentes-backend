package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.formulario.FormularioVersionRepository;
import com.cunoc.puentes.formulario.FormularioVersionService;
import com.cunoc.puentes.formulario.dto.CampoExtraDTO;
import com.cunoc.puentes.formulario.dto.CrearFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.FormularioVersionDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * B2 contra PostgreSQL real: publicar una versión compuesta copia el juego vigente de la versión
 * anterior, para que sus inspecciones calculen IC y generen órdenes.
 */
@SpringBootTest
class ClonacionPesosAlPublicarIntegracionTest extends PruebaIntegracion {

  @Autowired private FormularioVersionService formularios;
  @Autowired private FormularioVersionRepository repositorio;
  @Autowired private MetadatosDanoFormulario metadatos;
  @Autowired private ConfiguracionPesosService configuracion;
  @Autowired private JdbcTemplate jdbc;

  private UUID juegoCreado;
  private UUID administradorCreado;

  @BeforeEach
  void autenticarComoAdministrador() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                administrador().toString(),
                "no-aplica",
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))));
  }

  /**
   * La auditoría toma el identificador del principal y lo referencia como clave foránea: debe ser
   * un usuario real. Si la base de pruebas no tiene administradores, esta clase crea uno y lo borra
   * al terminar, para no alterar las pruebas que cuentan destinatarios de avisos.
   */
  private UUID administrador() {
    List<UUID> existentes =
        jdbc.query(
            "SELECT id FROM usuario WHERE rol = 'ADMINISTRADOR' ORDER BY creado_en LIMIT 1",
            (fila, numero) -> fila.getObject("id", UUID.class));
    if (!existentes.isEmpty()) {
      return existentes.getFirst();
    }
    UUID nuevo = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en)"
            + " VALUES (?, 'clon-admin@prueba.local', 'Administrador de prueba', 'no-aplica',"
            + " 'ADMINISTRADOR', 'ACTIVO', now())",
        nuevo);
    administradorCreado = nuevo;
    return nuevo;
  }

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
    jdbc.update(
        "DELETE FROM peso_elemento WHERE version_pesos_id IN (SELECT id FROM version_pesos"
            + " WHERE formulario_version_id IN (SELECT id FROM formulario_version WHERE codigo"
            + " LIKE 'CLON-%'))");
    jdbc.update(
        "DELETE FROM version_pesos WHERE formulario_version_id IN (SELECT id FROM"
            + " formulario_version WHERE codigo LIKE 'CLON-%')");
    jdbc.update("DELETE FROM formulario_version WHERE codigo LIKE 'CLON-%'");
    if (juegoCreado != null) {
      jdbc.update("DELETE FROM peso_elemento WHERE version_pesos_id = ?", juegoCreado);
      jdbc.update("DELETE FROM version_pesos WHERE id = ?", juegoCreado);
    }
    // Publicar retira la versión anterior: la semilla vuelve a ser la activa
    jdbc.update("UPDATE formulario_version SET estado = 'ACTIVA' WHERE codigo = 'V1.0.0'");
    if (administradorCreado != null) {
      jdbc.update("DELETE FROM auditoria WHERE usuario_id = ?", administradorCreado);
      jdbc.update("DELETE FROM usuario WHERE id = ?", administradorCreado);
      administradorCreado = null;
    }
  }

  @Test
  @DisplayName("Publicar una versión compuesta copia el juego vigente, con el mismo acta y pesos")
  void publicarCopiaElJuegoVigenteDeLaVersionAnterior() {
    UUID semilla = repositorio.findByCodigo("V1.0.0").orElseThrow().getId();
    var elementos = metadatos.obtener(semilla);
    List<PesoConfigurado> pesos =
        elementos.referencias().stream()
            .sorted()
            .map(referencia -> new PesoConfigurado(referencia, new BigDecimal("1.500")))
            .toList();
    juegoCreado = configuracion.crear(new JuegoPesos(semilla, "Acta CLON-001", pesos), elementos);

    FormularioVersionDTO borrador =
        formularios.crearBorrador(
            new CrearFormularioVersionDTO(
                "CLON-1",
                null,
                List.of(
                    new CampoExtraDTO(
                        "otros",
                        "estado_drenaje",
                        "Estado del drenaje",
                        "opcion",
                        false,
                        List.of("Bueno", "Malo"),
                        null,
                        null,
                        null,
                        null))));

    formularios.publicar(borrador.id(), null);

    UUID nuevaVersion = repositorio.findByCodigo("CLON-1").orElseThrow().getId();
    Integer juegos =
        jdbc.queryForObject(
            "SELECT count(*) FROM version_pesos WHERE formulario_version_id = ?",
            Integer.class,
            nuevaVersion);
    String acta =
        jdbc.queryForObject(
            "SELECT acta FROM version_pesos WHERE formulario_version_id = ?",
            String.class,
            nuevaVersion);
    Integer filas =
        jdbc.queryForObject(
            "SELECT count(*) FROM peso_elemento pe JOIN version_pesos vp ON vp.id ="
                + " pe.version_pesos_id WHERE vp.formulario_version_id = ?",
            Integer.class,
            nuevaVersion);

    assertThat(juegos).isEqualTo(1);
    assertThat(acta).isEqualTo("Acta CLON-001");
    assertThat(filas).isEqualTo(elementos.referencias().size());
  }
}
