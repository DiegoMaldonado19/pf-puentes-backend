package com.cunoc.puentes.puente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.puente.dto.AprobarSolicitudDTO;
import com.cunoc.puentes.puente.dto.CrearPuenteDTO;
import com.cunoc.puentes.puente.dto.CrearSolicitudPuenteDTO;
import com.cunoc.puentes.puente.dto.DarDeBajaPuenteDTO;
import com.cunoc.puentes.puente.dto.PuenteDTO;
import com.cunoc.puentes.puente.dto.RechazarSolicitudDTO;
import com.cunoc.puentes.puente.dto.SolicitudPuenteDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Sin transacción de prueba. Usa Cantel (0914) para no cruzarse con otros fixtures. */
@SpringBootTest
class SolicitudPuenteIntegracionTest extends PruebaIntegracion {

  private static final UUID CATEDRATICO = UUID.fromString("019a0030-0000-7000-8000-000000000001");
  private static final UUID ADMIN = UUID.fromString("019a0030-0000-7000-8000-000000000002");
  private static final double LAT = 14.8090;
  private static final double LON = -91.4560;

  @Autowired SolicitudPuenteService solicitudes;
  @Autowired PuenteService puentes;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cuentas() {
    jdbc.update(
        "INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol) VALUES"
            + " (?, 'catedratico@solicitud-prueba.com', 'Catedrático', 'hash', 'CATEDRATICO'),"
            + " (?, 'admin@solicitud-prueba.com', 'Admin', 'hash', 'ADMINISTRADOR')",
        CATEDRATICO,
        ADMIN);
  }

  @AfterEach
  void limpiar() {
    jdbc.update(
        "DELETE FROM auditoria WHERE entidad IN ('puente', 'solicitud_puente') AND (entidad_id IN"
            + " (SELECT id FROM puente WHERE municipio_codigo = '0914') OR entidad_id IN"
            + " (SELECT id FROM solicitud_puente WHERE municipio_codigo = '0914'))");
    jdbc.update("DELETE FROM solicitud_puente WHERE municipio_codigo = '0914'");
    jdbc.update("DELETE FROM puente WHERE municipio_codigo = '0914'");
    jdbc.update("DELETE FROM correlativo_puente WHERE municipio_codigo = '0914'");
    jdbc.update("DELETE FROM usuario WHERE id IN (?, ?)", CATEDRATICO, ADMIN);
  }

  SolicitudPuenteDTO solicitar(double lat) {
    return solicitudes.solicitar(
        CATEDRATICO,
        new CrearSolicitudPuenteDTO("Solicitado", "09", "0914", "RD-QUE-1", lat, LON, null));
  }

  int auditorias(String entidad, String accion, UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM auditoria WHERE entidad = ? AND accion = ? AND entidad_id = ?",
        Integer.class,
        entidad,
        accion,
        id);
  }

  @Test
  void aprobarCreaElPuenteYQuedaTodoAuditado() {
    SolicitudPuenteDTO pendiente = solicitar(LAT);

    SolicitudPuenteDTO aprobada =
        solicitudes.aprobar(pendiente.id(), ADMIN, new AprobarSolicitudDTO(null));

    PuenteDTO puente = puentes.detalle(aprobada.puenteId(), false);
    assertThat(aprobada.estado()).isEqualTo(EstadoSolicitud.APROBADA);
    assertThat(puente.codigo()).isEqualTo("GT-09-0914-0001");
    assertThat(puente.nombre()).isEqualTo("Solicitado");
    assertThat(puente.anioConstruccion()).isNull();
    assertThat(auditorias("solicitud_puente", "CREAR", pendiente.id())).isEqualTo(1);
    assertThat(auditorias("solicitud_puente", "CAMBIAR_ESTADO", pendiente.id())).isEqualTo(1);
    assertThat(auditorias("puente", "CREAR", puente.id())).isEqualTo(1);
  }

  @Test
  void rechazarExigeMotivoTambienEnLaBase() {
    SolicitudPuenteDTO pendiente = solicitar(LAT);

    SolicitudPuenteDTO rechazada =
        solicitudes.rechazar(pendiente.id(), ADMIN, new RechazarSolicitudDTO("Fuera de la ruta"));

    assertThat(rechazada.motivoRechazo()).isEqualTo("Fuera de la ruta");
    assertThat(
            jdbc.queryForObject(
                "SELECT motivo FROM auditoria WHERE entidad = 'solicitud_puente'"
                    + " AND accion = 'CAMBIAR_ESTADO' AND entidad_id = ?",
                String.class,
                pendiente.id()))
        .isEqualTo("Fuera de la ruta");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE solicitud_puente SET motivo_rechazo = '  ' WHERE id = ?",
                    pendiente.id()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void dosAprobacionesSimultaneasCreanUnSoloPuente() throws Exception {
    SolicitudPuenteDTO pendiente = solicitar(LAT);
    CountDownLatch salida = new CountDownLatch(1);
    List<Future<SolicitudPuenteDTO>> intentos = new ArrayList<>();
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 2; i++) {
        Callable<SolicitudPuenteDTO> aprobar =
            () -> {
              salida.await();
              return solicitudes.aprobar(pendiente.id(), ADMIN, new AprobarSolicitudDTO(null));
            };
        intentos.add(ejecutor.submit(aprobar));
      }
      salida.countDown();
      int aprobadas = 0;
      int conflictos = 0;
      for (Future<SolicitudPuenteDTO> intento : intentos) {
        try {
          intento.get();
          aprobadas++;
        } catch (ExecutionException e) {
          assertThat(e.getCause()).isInstanceOf(NegocioException.class);
          conflictos++;
        }
      }
      assertThat(aprobadas).isEqualTo(1);
      assertThat(conflictos).isEqualTo(1);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM puente WHERE municipio_codigo = '0914'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void laBajaEsLogicaAuditadaYNoLiberaElCodigo() {
    PuenteDTO puente =
        puentes.crear(new CrearPuenteDTO("Viejo", "09", "0914", "RD-1", LAT, LON, 1950, null));

    PuenteDTO baja = puentes.darDeBaja(puente.id(), new DarDeBajaPuenteDTO("Sustituido"));

    assertThat(baja.activo()).isFalse();
    assertThat(puentes.listar(null, "0914", null, null, false, 0, 20).totalElementos()).isZero();
    assertThat(puentes.listar(null, "0914", null, null, true, 0, 20).totalElementos()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT motivo FROM auditoria WHERE entidad = 'puente' AND accion = 'DAR_DE_BAJA'"
                    + " AND entidad_id = ?",
                String.class,
                puente.id()))
        .isEqualTo("Sustituido");
    // RN-INV-02: el nuevo puente en el mismo lugar recibe otro número; no se advierte del inactivo
    PuenteDTO nuevo =
        puentes.crear(new CrearPuenteDTO("Nuevo", "09", "0914", "RD-1", LAT, LON, 2026, null));
    assertThat(nuevo.codigo()).isEqualTo("GT-09-0914-0002");
  }
}
