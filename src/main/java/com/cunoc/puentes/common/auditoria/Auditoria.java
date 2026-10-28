package com.cunoc.puentes.common.auditoria;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auditoria extends EntidadBase {

  private UUID usuarioId;

  private String accion;

  private String entidad;

  private UUID entidadId;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> valorAnterior;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> valorNuevo;

  private String motivo;

  /** Nulo si la acción fue del Sistema. */
  private String origenIp;

  /** Agrupa las filas generadas por una misma operación de negocio. */
  private UUID correlacionId;
}
