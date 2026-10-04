package com.cunoc.puentes.archivo;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
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
public class Archivo {

  @Id private UUID id;

  private UUID inspeccionId;

  private UUID ordenMantenimientoId;

  @Enumerated(EnumType.STRING)
  private TipoArchivo tipo;

  private String clave;

  private String claveMiniatura;

  private long tamano;

  private String elementoRef;

  @JdbcTypeCode(SqlTypes.GEOGRAPHY)
  private Point ubicacion;

  private Instant capturadaEn;

  private Instant purgadoEn;

  @CreationTimestamp private Instant creadoEn;

  @UpdateTimestamp private Instant actualizadoEn;
}
