package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.mantenimiento.dto.PaginaOrdenesDTO;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectores de la bandeja confirmados por el equipo; la autorización del historial vive en su propia
 * fachada.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
public class ConsultaPropuestasService {
  private final ConsultaOrdenes consulta;

  ConsultaPropuestasService(ConsultaOrdenes consulta) {
    this.consulta = consulta;
  }

  public PaginaOrdenesDTO listar(UUID puenteId, int pagina, int tamano) {
    return PaginaOrdenesDTO.de(puenteId, consulta.listarPropuestas(puenteId, pagina, tamano));
  }
}
