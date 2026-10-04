package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrdenMantenimiento extends EntidadBase {

  private UUID puenteId;

  private UUID inspeccionId;

  @Enumerated(EnumType.STRING)
  private TipoOrden tipo;

  // DT-BE-07: solo la máquina de estados del servicio lo cambia
  @Setter(AccessLevel.PACKAGE)
  @Enumerated(EnumType.STRING)
  private EstadoOrden estado = EstadoOrden.PROPUESTA;

  private String descripcion;

  private String elementoRef;

  @Enumerated(EnumType.STRING)
  private Prioridad prioridadSugerida;

  @Enumerated(EnumType.STRING)
  private Prioridad prioridad;

  private String justificacionPrioridad;

  private LocalDate fechaProgramada;

  private LocalDate fechaEjecucion;

  private String responsable;

  private String motivo;
}
