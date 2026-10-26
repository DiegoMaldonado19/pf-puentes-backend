package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.usuario.EstadoUsuario;
import com.cunoc.puentes.usuario.Rol;
import jakarta.persistence.OptimisticLockException;
import java.util.UUID;
import org.hibernate.StaleStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única operación pública de cambio de estado (DT-BE-07). P7 y las historias de envío/publicación
 * la invocan; no hay endpoint genérico.
 */
@Service
public class CambioEstadoInspeccionService {

  private static final MaquinaEstadosInspeccion MAQUINA = new MaquinaEstadosInspeccion();

  private final InspeccionRepository inspecciones;
  private final EscrituraCambioEstadoInspeccion escritura;

  CambioEstadoInspeccionService(
      InspeccionRepository inspecciones, EscrituraCambioEstadoInspeccion escritura) {
    this.inspecciones = inspecciones;
    this.escritura = escritura;
  }

  @Transactional
  public void cambiar(
      UUID inspeccionId, EstadoInspeccion destino, UUID actorId, DatosTransicionInspeccion datos) {
    if (inspeccionId == null || destino == null || datos == null) {
      throw new IllegalArgumentException("La inspección, el destino y los datos son obligatorios.");
    }
    Inspeccion inspeccion =
        inspecciones
            .findById(inspeccionId)
            .filter(fila -> fila.getEliminadoEn() == null)
            .orElseThrow(CambioEstadoInspeccionService::noEncontrada);
    MAQUINA.validar(inspeccion.getEstado(), destino, datos);
    autorizar(inspeccion, destino, actorId);
    try {
      escritura.aplicar(inspeccion, destino, actorId, datos);
      if (destino == EstadoInspeccion.CAMBIOS_SOLICITADOS) {
        aplicarRetornoABorrador(inspeccion, datos.revisionId());
      }
    } catch (RuntimeException e) {
      if (esConflictoDeVersion(e)) {
        throw new NegocioException(
            HttpStatus.CONFLICT,
            "conflicto-de-version",
            "Conflicto de versión",
            "Otro proceso cambió la inspección al mismo tiempo.");
      }
      throw e;
    }
  }

  private static boolean esConflictoDeVersion(Throwable error) {
    while (error != null) {
      if (error instanceof OptimisticLockingFailureException
          || error instanceof OptimisticLockException
          || error instanceof StaleStateException) {
        return true;
      }
      error = error.getCause();
    }
    return false;
  }

  private void aplicarRetornoABorrador(Inspeccion inspeccion, UUID revisionId) {
    Authentication previa = SecurityContextHolder.getContext().getAuthentication();
    SecurityContextHolder.clearContext();
    try {
      escritura.aplicar(
          inspeccion,
          EstadoInspeccion.BORRADOR,
          null,
          DatosTransicionInspeccion.de(null, revisionId));
    } finally {
      SecurityContextHolder.getContext().setAuthentication(previa);
    }
  }

  private void autorizar(Inspeccion inspeccion, EstadoInspeccion destino, UUID actorId) {
    EstadoInspeccion origen = inspeccion.getEstado();
    if (origen == EstadoInspeccion.CAMBIOS_SOLICITADOS && destino == EstadoInspeccion.BORRADOR) {
      if (actorId != null) {
        throw prohibido(
            "accion-no-autorizada", "Solo el Sistema devuelve la inspección a borrador.");
      }
      return;
    }
    if (actorId == null) {
      throw prohibido("accion-no-autorizada", "Esta transición requiere un actor con cuenta.");
    }
    if (origen == EstadoInspeccion.BORRADOR && destino == EstadoInspeccion.ENVIADA) {
      if (!actorId.equals(inspeccion.getAutorId())) {
        throw noEncontrada();
      }
      return;
    }
    Cuenta cuenta = cuenta(actorId);
    if (origen == EstadoInspeccion.ENVIADA && destino == EstadoInspeccion.EN_REVISION) {
      exigirRevisorDistinto(inspeccion, actorId, cuenta);
      return;
    }
    if (origen == EstadoInspeccion.ENVIADA && destino == EstadoInspeccion.PUBLICADA) {
      if (!actorId.equals(inspeccion.getAutorId())
          || !cuenta.activa()
          || (cuenta.rol != Rol.CATEDRATICO && cuenta.rol != Rol.PROFESIONAL_EXTERNO)) {
        throw prohibido(
            "publicacion-directa-no-permitida",
            "Solo el autor Catedrático o Profesional activo puede publicar de forma directa.");
      }
      return;
    }
    if (origen == EstadoInspeccion.EN_REVISION
        && (destino == EstadoInspeccion.PUBLICADA
            || destino == EstadoInspeccion.CAMBIOS_SOLICITADOS
            || destino == EstadoInspeccion.RECHAZADA)) {
      exigirRevisorDistinto(inspeccion, actorId, cuenta);
      return;
    }
    throw prohibido("accion-no-autorizada", "El actor no puede disparar esta transición.");
  }

  private void exigirRevisorDistinto(Inspeccion inspeccion, UUID actorId, Cuenta cuenta) {
    if (actorId.equals(inspeccion.getAutorId())) {
      throw new NegocioException(
          HttpStatus.FORBIDDEN,
          "revisor-es-el-autor",
          "El revisor no puede ser el autor",
          "Un catedrático no abre ni resuelve la revisión de su propia inspección.");
    }
    if (!cuenta.activa() || cuenta.rol != Rol.CATEDRATICO) {
      throw prohibido(
          "accion-no-autorizada", "Solo un catedrático distinto del autor puede revisar.");
    }
  }

  private Cuenta cuenta(UUID actorId) {
    String rol =
        inspecciones.rolDeUsuario(actorId).orElseThrow(CambioEstadoInspeccionService::noEncontrada);
    String estado =
        inspecciones
            .estadoDeUsuario(actorId)
            .orElseThrow(CambioEstadoInspeccionService::noEncontrada);
    return new Cuenta(Rol.valueOf(rol), EstadoUsuario.valueOf(estado));
  }

  private static NegocioException noEncontrada() {
    return new NegocioException(
        HttpStatus.NOT_FOUND,
        "inspeccion-no-encontrada",
        "Inspección no encontrada",
        "La inspección no existe o no está disponible.");
  }

  private static NegocioException prohibido(String tipo, String detalle) {
    return new NegocioException(HttpStatus.FORBIDDEN, tipo, "Acción no permitida", detalle);
  }

  private record Cuenta(Rol rol, EstadoUsuario estado) {
    boolean activa() {
      return estado == EstadoUsuario.ACTIVO;
    }
  }
}
