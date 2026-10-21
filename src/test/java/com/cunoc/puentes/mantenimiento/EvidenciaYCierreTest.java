package com.cunoc.puentes.mantenimiento;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.archivo.AlmacenamientoArchivos;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.mantenimiento.dto.CierreOrdenDTO;
import com.cunoc.puentes.usuario.Rol;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.context.request.RequestContextHolder;

/** Transacciones reales y objetos reales en MinIO; sin rollback implícito de prueba. */
@SpringBootTest
@AutoConfigureMockMvc
@Sql("/mantenimiento/evidencia-cierre-ordenes.sql")
class EvidenciaYCierreTest extends PruebaIntegracion {
  private static final UUID PUENTE = id(1);
  private static final UUID ADMIN = id(3);
  private static final UUID CATEDRATICO = id(4);
  private static final UUID ORDEN = id(12);
  private static final String CIERRE =
      "{\"fecha_ejecucion\":\"2026-10-20\",\"responsable\":\"Cuadrilla de mantenimiento\"}";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrdenMantenimientoService servicio;
  @Autowired EvidenciasOrdenService evidencias;
  @Autowired org.springframework.transaction.PlatformTransactionManager transacciones;
  @Autowired AlmacenamientoArchivos almacen;

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    var claves =
        jdbc.queryForList(
            "SELECT clave, clave_miniatura FROM archivo WHERE orden_mantenimiento_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?))",
            PUENTE,
            id(2));
    claves.forEach(
        fila -> {
          almacen.eliminar((String) fila.get("clave"));
          if (fila.get("clave_miniatura") != null)
            almacen.eliminar((String) fila.get("clave_miniatura"));
        });
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad_id IN (SELECT id FROM archivo WHERE orden_mantenimiento_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?)))",
        PUENTE,
        id(2));
    jdbc.update(
        "DELETE FROM archivo WHERE orden_mantenimiento_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?))",
        PUENTE,
        id(2));
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?))",
        PUENTE,
        id(2));
    jdbc.update("DELETE FROM orden_mantenimiento WHERE puente_id IN (?, ?)", PUENTE, id(2));
    jdbc.update("DELETE FROM inspeccion WHERE id = ?", id(8));
    jdbc.update("DELETE FROM formulario_version WHERE id = ?", id(7));
    jdbc.update("DELETE FROM puente WHERE id IN (?, ?)", PUENTE, id(2));
    jdbc.update("DELETE FROM usuario WHERE id = ?", id(6));
    jdbc.update("DELETE FROM usuario WHERE correo LIKE '%@cierre-prueba.com'");
    jdbc.update("DELETE FROM municipio WHERE codigo = '9696'");
    jdbc.update("DELETE FROM departamento WHERE codigo = '96'");
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ADMINISTRADOR", "CATEDRATICO"})
  void guardaFotoRealConMetadatosConsultaYCierraConAuditoria(Rol rol) throws Exception {
    UUID usuario = rol == Rol.ADMINISTRADOR ? ADMIN : CATEDRATICO;
    mvc.perform(
            foto(PUENTE, ORDEN, id(20))
                .param("latitud", "14.62")
                .param("longitud", "-90.51")
                .param("elemento_ref", "subestructura.estribo")
                .with(como(rol, usuario)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tipo").value("JPEG"))
        .andExpect(jsonPath("$.latitud").value(14.62))
        .andExpect(jsonPath("$.longitud").value(-90.51))
        .andExpect(jsonPath("$.elemento_ref").value("subestructura.estribo"))
        .andExpect(jsonPath("$.url_miniatura").isNotEmpty());
    String clave =
        jdbc.queryForObject("SELECT clave FROM archivo WHERE id = ?", String.class, id(20));
    try (HttpClient cliente = HttpClient.newHttpClient()) {
      var respuesta =
          cliente.send(
              HttpRequest.newBuilder(URI.create(almacen.obtenerUrlFirmada(clave))).GET().build(),
              HttpResponse.BodyHandlers.ofByteArray());
      assertThat(respuesta.statusCode()).isEqualTo(200);
      assertThat(respuesta.body()).isEqualTo(jpeg());
    }
    mvc.perform(get(ruta(PUENTE, ORDEN) + "/evidencias").with(como(rol, usuario)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orden_id").value(ORDEN.toString()))
        .andExpect(jsonPath("$.puente_id").value(PUENTE.toString()))
        .andExpect(jsonPath("$.total_elements").value(1));
    mvc.perform(
            post(ruta(PUENTE, ORDEN) + "/cierre")
                .with(como(rol, usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CIERRE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EJECUTADA"))
        .andExpect(jsonPath("$.fecha_ejecucion").value("2026-10-20"))
        .andExpect(jsonPath("$.responsable").value("Cuadrilla de mantenimiento"));
    assertThat(
            jdbc.queryForMap(
                "SELECT usuario_id, valor_anterior->>'estado' AS anterior, valor_nuevo->>'estado' AS nuevo FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id = ?",
                ORDEN))
        .containsEntry("usuario_id", usuario)
        .containsEntry("anterior", "EN_EJECUCION")
        .containsEntry("nuevo", "EJECUTADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'archivo' AND entidad_id = ? AND accion = 'CREAR'",
                Long.class,
                id(20)))
        .isEqualTo(1);
    mvc.perform(get(ruta(PUENTE, ORDEN) + "/evidencias").with(como(rol, usuario)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].url").isNotEmpty());
    mvc.perform(foto(PUENTE, ORDEN, id(21)).with(como(rol, usuario)))
        .andExpect(status().isConflict());
  }

  @Test
  void permiteSubirProgramadaYReintentarMismoIdSinDuplicarArchivoNiAuditoria() throws Exception {
    for (int i = 0; i < 2; i++)
      mvc.perform(foto(PUENTE, id(10), id(20)).with(como(Rol.ADMINISTRADOR, ADMIN)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(id(20).toString()));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM archivo WHERE id = ?", Long.class, id(20)))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad_id = ?", Long.class, id(20)))
        .isEqualTo(1);
    mvc.perform(foto(PUENTE, ORDEN, id(20)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isConflict());
  }

  @ParameterizedTest
  @ValueSource(ints = {11, 13, 14, 15})
  void estadosNoEditablesNoAceptanEvidencia(int orden) throws Exception {
    mvc.perform(foto(PUENTE, id(orden), id(20)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isConflict());
    assertThat(numeroArchivos()).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void otrosRolesNoSubenLeenNiCierran(Rol rol) throws Exception {
    UUID usuario = rol == Rol.ESTUDIANTE ? id(5) : id(6);
    mvc.perform(foto(PUENTE, ORDEN, id(20)).with(como(rol, usuario)))
        .andExpect(status().isForbidden());
    mvc.perform(get(ruta(PUENTE, ORDEN) + "/evidencias").with(como(rol, usuario)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(ruta(PUENTE, ORDEN) + "/cierre")
                .with(como(rol, usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CIERRE))
        .andExpect(status().isForbidden());
    assertThat(numeroArchivos()).isZero();
    assertThat(estado()).isEqualTo("EN_EJECUCION");
  }

  @Test
  void sinSesionLasTresOperacionesDan401() throws Exception {
    mvc.perform(foto(PUENTE, ORDEN, id(20))).andExpect(status().isUnauthorized());
    mvc.perform(get(ruta(PUENTE, ORDEN) + "/evidencias")).andExpect(status().isUnauthorized());
    mvc.perform(
            post(ruta(PUENTE, ORDEN) + "/cierre")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CIERRE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ordenAjenaNoPuedeCargarseConsultarseNiCerrarse() throws Exception {
    mvc.perform(foto(id(2), ORDEN, id(20)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isNotFound());
    mvc.perform(get(ruta(id(2), ORDEN) + "/evidencias").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(ruta(id(2), ORDEN) + "/cierre")
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CIERRE))
        .andExpect(status().isNotFound());
    assertThat(numeroArchivos()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=2147483647&size=100"})
  void rechazaPaginacionInvalida(String consulta) throws Exception {
    mvc.perform(
            get(ruta(PUENTE, ORDEN) + "/evidencias?" + consulta)
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void exigeFirmasBinariasYMetadatosAntesDeGuardar() throws Exception {
    mvc.perform(
            multipart(ruta(PUENTE, ORDEN) + "/evidencias")
                .file(
                    new MockMultipartFile(
                        "foto",
                        "foto.jpg",
                        "image/jpeg",
                        "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .file(new MockMultipartFile("miniatura", "mini.jpg", "image/jpeg", jpeg()))
                .param("id", id(20).toString())
                .param("capturada_en", "2026-10-20T12:00:00Z")
                .with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(multipart(ruta(PUENTE, ORDEN) + "/evidencias").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            foto(PUENTE, ORDEN, id(20)).param("latitud", "91").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isBadRequest());
    assertThat(numeroArchivos()).isZero();
  }

  @Test
  void fotoEnOtraOrdenNoPermiteCerrarNiUnContadorDelClienteLaSustituye() throws Exception {
    mvc.perform(foto(PUENTE, id(10), id(20)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isCreated());
    mvc.perform(
            post(ruta(PUENTE, ORDEN) + "/cierre")
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CIERRE.replace("}", ",\"fotos_guardadas\":100}")))
        .andExpect(status().isUnprocessableEntity());
    assertThat(estado()).isEqualTo("EN_EJECUCION");
  }

  @Test
  void pdfYFotoPurgadaNoCuentanYLaPaginaFiltraAntesDePaginar() throws Exception {
    mvc.perform(foto(PUENTE, ORDEN, id(20)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isCreated());
    jdbc.update("UPDATE archivo SET purgado_en = now() WHERE id = ?", id(20));
    jdbc.update(
        "INSERT INTO archivo (id, orden_mantenimiento_id, tipo, clave, tamano) VALUES (?, ?, 'PDF', 'prueba-cierre.pdf', 8)",
        id(21),
        ORDEN);
    mvc.perform(
            get(ruta(PUENTE, ORDEN) + "/evidencias?size=1").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(0))
        .andExpect(jsonPath("$.content").isEmpty());
    mvc.perform(
            post(ruta(PUENTE, ORDEN) + "/cierre")
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CIERRE))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(foto(PUENTE, ORDEN, id(22)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isCreated());
    mvc.perform(
            get(ruta(PUENTE, ORDEN) + "/evidencias?size=1").with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id(22).toString()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"fecha_ejecucion\":\"2026-10-20\",\"responsable\":\" \"}",
        "{\"fecha_ejecucion\":\"fecha\",\"responsable\":\"R\"}"
      })
  void cierreInvalidoDa400(String cuerpo) throws Exception {
    mvc.perform(
            post(ruta(PUENTE, ORDEN) + "/cierre")
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isBadRequest());
    assertThat(estado()).isEqualTo("EN_EJECUCION");
  }

  @ParameterizedTest
  @ValueSource(ints = {10, 11, 13, 14, 15})
  void soloEnEjecucionPuedeCerrarse(int orden) throws Exception {
    mvc.perform(
            post(ruta(PUENTE, id(orden)) + "/cierre")
                .with(como(Rol.ADMINISTRADOR, ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CIERRE))
        .andExpect(status().isConflict());
  }

  @Test
  void longitudUnicodeYFalloDeBitacoraConservanLaOrden() throws Exception {
    mvc.perform(foto(PUENTE, ORDEN, id(20)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isCreated());
    autenticar(ADMIN);
    assertThatThrownBy(
            () ->
                servicio.cerrar(
                    PUENTE, ORDEN, new CierreOrdenDTO(LocalDate.of(2026, 10, 20), "a".repeat(151))))
        .isInstanceOf(NegocioException.class);
    autenticar(id(99));
    assertThatThrownBy(
            () ->
                servicio.cerrar(
                    PUENTE, ORDEN, new CierreOrdenDTO(LocalDate.of(2026, 10, 20), "Responsable")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(estado()).isEqualTo("EN_EJECUCION");
    assertThat(
            jdbc.queryForObject(
                "SELECT responsable FROM orden_mantenimiento WHERE id = ?", String.class, ORDEN))
        .isNull();
    autenticar(ADMIN);
    assertThat(
            servicio
                .cerrar(
                    PUENTE, ORDEN, new CierreOrdenDTO(LocalDate.of(2026, 10, 20), "🚧".repeat(150)))
                .responsable())
        .isEqualTo("🚧".repeat(150));
  }

  @Test
  void dosCierresConcurrentesSoloProducenUnaEjecucionAuditada() throws Exception {
    mvc.perform(foto(PUENTE, ORDEN, id(20)).with(como(Rol.ADMINISTRADOR, ADMIN)))
        .andExpect(status().isCreated());
    CountDownLatch listas = new CountDownLatch(2);
    CountDownLatch salida = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var primera = ejecutor.submit(() -> cerrarConcurrente(ADMIN, listas, salida));
      var segunda = ejecutor.submit(() -> cerrarConcurrente(CATEDRATICO, listas, salida));
      boolean preparadas = listas.await(10, TimeUnit.SECONDS);
      salida.countDown();
      assertThat(preparadas).isTrue();
      assertThat(List.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE entidad = 'orden_mantenimiento' AND entidad_id = ?",
                Long.class,
                ORDEN))
        .isEqualTo(1);
  }

  @Test
  void cierreEsperaLaSubidaEnCursoYCuentaLaFotoConfirmada() throws Exception {
    CountDownLatch subida = new CountDownLatch(1);
    CountDownLatch confirmar = new CountDownLatch(1);
    CountDownLatch cerrando = new CountDownLatch(1);
    byte[] imagen = jpeg();
    var datos =
        new com.cunoc.puentes.archivo.dto.SubirFotoDTO(
            id(20),
            new MockMultipartFile("foto", "foto.jpg", "image/jpeg", imagen),
            new MockMultipartFile("miniatura", "mini.jpg", "image/jpeg", imagen),
            null,
            null,
            null,
            java.time.Instant.parse("2026-10-20T12:00:00Z"));
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var carga =
          ejecutor.submit(
              () -> {
                try {
                  autenticar(ADMIN);
                  new org.springframework.transaction.support.TransactionTemplate(transacciones)
                      .executeWithoutResult(
                          tx -> {
                            evidencias.subir(PUENTE, ORDEN, datos);
                            subida.countDown();
                            try {
                              if (!confirmar.await(10, TimeUnit.SECONDS))
                                throw new IllegalStateException("No se liberó la carga.");
                            } catch (InterruptedException error) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(error);
                            }
                          });
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertThat(subida.await(10, TimeUnit.SECONDS)).isTrue();
        var cierre =
            ejecutor.submit(
                () -> {
                  try {
                    autenticar(CATEDRATICO);
                    cerrando.countDown();
                    return servicio.cerrar(
                        PUENTE, ORDEN, new CierreOrdenDTO(LocalDate.of(2026, 10, 20), "Cuadrilla"));
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                });
        assertThat(cerrando.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> cierre.get(200, TimeUnit.MILLISECONDS))
            .isInstanceOf(java.util.concurrent.TimeoutException.class);
        confirmar.countDown();
        carga.get(10, TimeUnit.SECONDS);
        assertThat(cierre.get(10, TimeUnit.SECONDS).estado()).isEqualTo(EstadoOrden.EJECUTADA);
      } finally {
        confirmar.countDown();
      }
    }
  }

  private int cerrarConcurrente(UUID usuario, CountDownLatch listas, CountDownLatch salida)
      throws InterruptedException {
    try {
      autenticar(usuario);
      listas.countDown();
      if (!salida.await(10, TimeUnit.SECONDS))
        throw new IllegalStateException("La prueba no liberó el cierre.");
      servicio.cerrar(PUENTE, ORDEN, new CierreOrdenDTO(LocalDate.of(2026, 10, 20), "Responsable"));
      return 200;
    } catch (NegocioException error) {
      return error.getStatusCode().value();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static void autenticar(UUID usuario) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                usuario.toString(),
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))));
  }

  private long numeroArchivos() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM archivo WHERE orden_mantenimiento_id IN (SELECT id FROM orden_mantenimiento WHERE puente_id IN (?, ?))",
        Long.class,
        PUENTE,
        id(2));
  }

  private String estado() {
    return jdbc.queryForObject(
        "SELECT estado FROM orden_mantenimiento WHERE id = ?", String.class, ORDEN);
  }

  private static MockMultipartHttpServletRequestBuilder foto(UUID puente, UUID orden, UUID archivo)
      throws Exception {
    return multipart(ruta(puente, orden) + "/evidencias")
        .file(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", jpeg()))
        .file(new MockMultipartFile("miniatura", "mini.jpg", "image/jpeg", jpeg()))
        .param("id", archivo.toString())
        .param("capturada_en", "2026-10-20T12:00:00Z");
  }

  private static byte[] jpeg() throws Exception {
    var salida = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", salida);
    return salida.toByteArray();
  }

  private static UUID id(int numero) {
    return UUID.fromString("019a0030-0000-7000-8000-%012d".formatted(numero));
  }

  private static String ruta(UUID puente, UUID orden) {
    return "/api/v1/puentes/" + puente + "/ordenes-mantenimiento/" + orden;
  }
}
