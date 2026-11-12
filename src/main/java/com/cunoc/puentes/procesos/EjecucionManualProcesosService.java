package com.cunoc.puentes.procesos;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.RegistroManualAuditoria;
import com.cunoc.puentes.mantenimiento.AlertasInspeccionJob;
import com.cunoc.puentes.procesos.dto.EjecucionProcesoDTO;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * B10: ejecuta ahora un proceso programado. Toma el mismo bloqueo de ShedLock que su ejecución
 * automática, así una ejecución manual nunca corre a la vez que la programada o la de otra réplica;
 * si el bloqueo está tomado responde 409 en vez de omitir la petición en silencio.
 */
@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class EjecucionManualProcesosService {

  /** Proceso manual: nombre del bloqueo compartido con la programación, tiempo máximo y tarea. */
  record Proceso(String bloqueo, Duration maximoBloqueo, Runnable tarea) {}

  private static final String ENTIDAD_AUDITADA = "proceso";

  private final LockProvider bloqueos;
  private final Map<String, Proceso> procesos;
  private final RegistroManualAuditoria auditoria;
  private final Clock reloj;

  @Autowired
  public EjecucionManualProcesosService(
      LockProvider bloqueos, AlertasInspeccionJob alertas, RegistroManualAuditoria auditoria) {
    this(bloqueos, alertas, auditoria, Clock.systemUTC());
  }

  EjecucionManualProcesosService(
      LockProvider bloqueos,
      AlertasInspeccionJob alertas,
      RegistroManualAuditoria auditoria,
      Clock reloj) {
    this.bloqueos = bloqueos;
    this.auditoria = auditoria;
    this.reloj = reloj;
    this.procesos =
        Map.of(
            "alertas-inspeccion",
            new Proceso(
                "alertas-inspeccion-vencida", Duration.ofMinutes(30), alertas::ejecutarSinBloqueo));
  }

  public EjecucionProcesoDTO ejecutar(String nombre) {
    Proceso proceso = procesos.get(nombre);
    if (proceso == null) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "proceso-no-encontrado",
          "Proceso no encontrado",
          "No existe un proceso manual llamado: " + nombre);
    }
    var inicio = reloj.instant();
    SimpleLock candado = adquirir(proceso, inicio);
    try {
      proceso.tarea().run();
    } finally {
      candado.unlock();
    }
    UUID id = UUID.nameUUIDFromBytes(nombre.getBytes(StandardCharsets.UTF_8));
    auditoria.registrar(Acciones.EJECUTAR_PROCESO, ENTIDAD_AUDITADA, id, null);
    return new EjecucionProcesoDTO(id, nombre, inicio, reloj.instant());
  }

  private SimpleLock adquirir(Proceso proceso, Instant inicio) {
    Optional<SimpleLock> candado =
        bloqueos.lock(
            new LockConfiguration(
                inicio, proceso.bloqueo(), proceso.maximoBloqueo(), Duration.ZERO));
    return candado.orElseThrow(
        () ->
            new NegocioException(
                HttpStatus.CONFLICT,
                "proceso-en-ejecucion",
                "Proceso en ejecución",
                "El proceso ya se está ejecutando. Espere a que termine e intente de nuevo."));
  }
}
