package com.cunoc.puentes.revision;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.inspeccion.PublicacionInspeccionService;
import com.cunoc.puentes.usuario.EstadoCuentaService;
import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevisionService {
  private final RevisionRepository revisiones;
  private final BandejaRevisionRepository bandeja;
  private final PublicacionInspeccionService inspecciones;
  private final EstadoCuentaService cuentas;

  RevisionService(
      RevisionRepository revisiones,
      BandejaRevisionRepository bandeja,
      PublicacionInspeccionService inspecciones,
      EstadoCuentaService cuentas) {
    this.revisiones = revisiones;
    this.bandeja = bandeja;
    this.inspecciones = inspecciones;
    this.cuentas = cuentas;
  }

  @Transactional(readOnly = true)
  public Page<BandejaRevisionDTO> pendientes(UUID actor, String estado, Pageable pagina) {
    exigirDocente(actor);
    if (estado != null && !estado.equals("ENVIADA") && !estado.equals("EN_REVISION")) {
      throw error(HttpStatus.BAD_REQUEST, "estado-invalido", "Filtra por ENVIADA o EN_REVISION.");
    }
    return bandeja.pendientes(
        actor, estado, PageRequest.of(pagina.getPageNumber(), Math.min(pagina.getPageSize(), 100)));
  }

  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "revision", entidadId = "#result.id")
  public RevisionAbiertaDTO abrir(UUID actor, UUID inspeccionId) {
    exigirDocente(actor);
    String estado =
        bandeja
            .bloquear(actor, inspeccionId)
            .orElseThrow(
                () ->
                    error(
                        HttpStatus.NOT_FOUND,
                        "inspeccion-no-encontrada",
                        "La inspección no existe o no pertenece a tus estudiantes."));
    if (!estado.equals("ENVIADA")) {
      throw error(
          HttpStatus.CONFLICT,
          "revision-no-disponible",
          "Solo puedes abrir una inspección enviada; actualiza la bandeja.");
    }
    if (revisiones.findByInspeccionIdAndVeredictoIsNull(inspeccionId).isPresent()) {
      throw error(
          HttpStatus.CONFLICT, "revision-activa", "La inspección ya tiene un ciclo abierto.");
    }
    Revision revision = new Revision();
    revision.setInspeccionId(inspeccionId);
    revision.setRevisorId(actor);
    revision = revisiones.saveAndFlush(revision);
    inspecciones.abrirRevision(inspeccionId, actor, revision.getId());
    return RevisionAbiertaDTO.de(revision);
  }

  private void exigirDocente(UUID actor) {
    if (cuentas.rolSiEstaActiva(actor).filter(Rol.CATEDRATICO::equals).isEmpty()) {
      throw error(
          HttpStatus.FORBIDDEN, "cuenta-no-habilitada", "Se requiere un catedrático activo.");
    }
  }

  private static NegocioException error(HttpStatus status, String tipo, String detalle) {
    return new NegocioException(status, tipo, "Revisión académica", detalle);
  }
}
