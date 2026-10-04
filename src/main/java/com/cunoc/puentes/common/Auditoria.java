package com.cunoc.puentes.common;

import jakarta.persistence.Entity;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
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
}
