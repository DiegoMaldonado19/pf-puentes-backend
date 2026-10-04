package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.indice.Anulacion;
import com.cunoc.puentes.indice.EstadoCondicion;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/** No extiende EntidadBase: el id lo genera el cliente (DT-OFF-05). */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inspeccion {

  @Id private UUID id;

  private UUID puenteId;

  private UUID autorId;

  private UUID formularioVersionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "supersede_a_id")
  private Inspeccion supersedeA;

  // DT-BE-07: solo la máquina de estados del servicio lo cambia
  @Setter(AccessLevel.PACKAGE)
  @Enumerated(EnumType.STRING)
  private EstadoInspeccion estado = EstadoInspeccion.BORRADOR;

  private LocalDate fechaInspeccion;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> datos = new HashMap<>();

  @JdbcTypeCode(SqlTypes.GEOGRAPHY)
  private Point ubicacionInicio;

  private String dispositivoId;

  private Instant enviadaEn;

  private Instant sincronizadaEn;

  private Instant publicadaEn;

  private UUID versionPesosId;

  private BigDecimal indiceCondicion;

  @Enumerated(EnumType.STRING)
  private EstadoCondicion estadoCalculado;

  @Enumerated(EnumType.STRING)
  private Anulacion anulacion;

  @Enumerated(EnumType.STRING)
  private EstadoCondicion estadoConfirmado;

  private String justificacionEstado;

  private Instant eliminadoEn;

  @CreationTimestamp private Instant creadoEn;

  @UpdateTimestamp private Instant actualizadoEn;
}
