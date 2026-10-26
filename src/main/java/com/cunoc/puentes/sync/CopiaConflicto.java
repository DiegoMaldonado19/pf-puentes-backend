package com.cunoc.puentes.sync;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import java.util.LinkedHashMap;
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
public class CopiaConflicto extends EntidadBase {

  private UUID inspeccionId;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> datos;

  public static CopiaConflicto de(UUID inspeccionId, Map<String, Object> datos) {
    CopiaConflicto copia = new CopiaConflicto();
    copia.setInspeccionId(inspeccionId);
    copia.setDatos(datos == null ? Map.of() : new LinkedHashMap<>(datos));
    return copia;
  }
}
