package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdenMantenimientoService {

  private final OrdenMantenimientoRepository ordenes;
  private final ValidacionOrigenOrden origen;
  private final AceptacionOrdenService aceptacion;
  private final InicioEjecucionOrdenService inicio;
  private final OrdenMantenimientoMapper mapper;

  OrdenMantenimientoService(
      OrdenMantenimientoRepository ordenes,
      ValidacionOrigenOrden origen,
      AceptacionOrdenService aceptacion,
      InicioEjecucionOrdenService inicio,
      OrdenMantenimientoMapper mapper) {
    this.ordenes = ordenes;
    this.origen = origen;
    this.aceptacion = aceptacion;
    this.inicio = inicio;
    this.mapper = mapper;
  }

  /** RN-MTO-02: acepta una propuesta existente; conserva su información de programación. */
  @Transactional
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  public OrdenMantenimientoDTO aceptar(UUID puenteId, UUID ordenId) {
    OrdenMantenimiento orden = obtenerBloqueada(puenteId, ordenId);
    // Bloquear antes de entrar al aspecto: su foto anterior debe ser la de la fila bloqueada.
    aceptacion.aceptar(orden);
    // El aspecto ya hizo flush: el DTO incluye la marca de actualización persistida.
    return mapper.aDTO(orden);
  }

  /** RN-MTO-04; actores de inicio confirmados por el equipo el 20/10/2026. */
  @Transactional
  @PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
  public OrdenMantenimientoDTO iniciarEjecucion(UUID puenteId, UUID ordenId) {
    OrdenMantenimiento orden = obtenerBloqueada(puenteId, ordenId);
    inicio.iniciar(orden);
    return mapper.aDTO(orden);
  }

  private OrdenMantenimiento obtenerBloqueada(UUID puenteId, UUID ordenId) {
    if (puenteId == null || ordenId == null) {
      throw new IllegalArgumentException("El puente y la orden son obligatorios.");
    }
    OrdenMantenimiento orden =
        ordenes
            .bloquear(puenteId, ordenId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "orden-no-encontrada",
                        "Orden no encontrada",
                        "La orden indicada no existe en el puente solicitado."));
    origen.validar(puenteId, orden.getInspeccionId());
    return orden;
  }
}
