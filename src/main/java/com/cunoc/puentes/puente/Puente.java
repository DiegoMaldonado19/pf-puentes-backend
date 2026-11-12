package com.cunoc.puentes.puente;

import com.cunoc.puentes.common.EntidadBase;
import com.cunoc.puentes.indice.EstadoCondicion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Puente extends EntidadBase {

  @Column(updatable = false) // RN-INV-02
  private String codigo;

  private String nombre;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Municipio municipio;

  private String ruta;

  @JdbcTypeCode(SqlTypes.GEOGRAPHY)
  private Point ubicacion;

  private Integer anioConstruccion;

  private boolean activo = true;

  private String motivoBaja;

  // RN-INV-08: cuándo y quién dieron de baja; NULL en bajas anteriores a V2026_11_11_1000
  private Instant bajaEn;

  private UUID bajaPor;

  // RN-INV-09: sin setter; solo cambian con la inspección publicada (actualizarCondicion)
  @Setter(AccessLevel.NONE)
  @Enumerated(EnumType.STRING)
  private EstadoCondicion estadoActual;

  @Setter(AccessLevel.NONE)
  private BigDecimal indiceCondicionActual;

  @Setter(AccessLevel.NONE)
  private LocalDate fechaUltimaInspeccion;

  void actualizarCondicion(EstadoCondicion estado, BigDecimal indice, LocalDate fecha) {
    this.estadoActual = estado;
    this.indiceCondicionActual = indice;
    this.fechaUltimaInspeccion = fecha;
  }
}
