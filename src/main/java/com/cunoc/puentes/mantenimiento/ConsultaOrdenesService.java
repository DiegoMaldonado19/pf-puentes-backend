package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import com.cunoc.puentes.mantenimiento.dto.PaginaOrdenesDTO;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historial/detalle: lectores confirmados por el equipo; no autoriza mutaciones. */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
public class ConsultaOrdenesService {
  private final ConsultaOrdenes consulta;

  ConsultaOrdenesService(ConsultaOrdenes consulta) {
    this.consulta = consulta;
  }

  public PaginaOrdenesDTO listar(UUID puenteId, int pagina, int tamano) {
    return PaginaOrdenesDTO.de(puenteId, consulta.listar(puenteId, pagina, tamano));
  }

  public OrdenMantenimientoDTO obtener(UUID puenteId, UUID ordenId) {
    return consulta.obtener(puenteId, ordenId);
  }
}
