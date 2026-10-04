package com.cunoc.puentes.formulario;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.Map;
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
public class FormularioVersion extends EntidadBase {

  private String codigo;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> esquema;

  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> mapeo;

  @Enumerated(EnumType.STRING)
  private EstadoFormulario estado = EstadoFormulario.BORRADOR;

  private Instant publicadaEn;
}
