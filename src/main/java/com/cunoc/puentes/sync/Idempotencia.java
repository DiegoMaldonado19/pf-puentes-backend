package com.cunoc.puentes.sync;

import com.cunoc.puentes.common.EntidadBase;
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
public class Idempotencia extends EntidadBase {

  private UUID usuarioId;

  private String clave;

  private int estadoHttp;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> respuesta;
}
