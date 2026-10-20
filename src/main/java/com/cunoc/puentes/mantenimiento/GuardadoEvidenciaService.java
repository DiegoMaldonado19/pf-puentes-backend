package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.archivo.ArchivoService;
import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import com.cunoc.puentes.archivo.dto.SubirFotoDTO;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reutiliza almacenamiento P1 y audita la creación, sin duplicar bitácora al reenviar el UUID. */
@Service
class GuardadoEvidenciaService {
  private final ArchivoService archivos;

  GuardadoEvidenciaService(ArchivoService archivos) {
    this.archivos = archivos;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.CREAR,
      entidad = "archivo",
      entidadId = "#result.id",
      condicion = "#nueva")
  public ArchivoDTO guardar(UUID ordenId, SubirFotoDTO datos, boolean nueva) {
    return archivos.guardarEvidencia(ordenId, datos);
  }
}
