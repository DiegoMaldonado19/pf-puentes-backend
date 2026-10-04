package com.cunoc.puentes.puente;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
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
public class SolicitudPuente extends EntidadBase {

  private UUID catedraticoId;

  private String nombre;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Municipio municipio;

  private String ruta;

  @JdbcTypeCode(SqlTypes.GEOGRAPHY)
  private Point ubicacion;

  @Enumerated(EnumType.STRING)
  private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

  private UUID administradorId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Puente puente;
}
