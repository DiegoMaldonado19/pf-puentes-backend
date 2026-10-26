package com.cunoc.puentes.sync;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Column;
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
  private Object respuesta;

  private Integer huellaVersion;

  @Column(columnDefinition = "bytea")
  private byte[] huellaSha256;

  @Column(columnDefinition = "bytea")
  private byte[] respuestaBytes;

  public byte[] getHuellaSha256() {
    return huellaSha256 == null ? null : huellaSha256.clone();
  }

  public void setHuellaSha256(byte[] huellaSha256) {
    this.huellaSha256 = huellaSha256 == null ? null : huellaSha256.clone();
  }

  public byte[] getRespuestaBytes() {
    return respuestaBytes == null ? null : respuestaBytes.clone();
  }

  public void setRespuestaBytes(byte[] respuestaBytes) {
    this.respuestaBytes = respuestaBytes == null ? null : respuestaBytes.clone();
  }

  public static Idempotencia de(
      UUID usuarioId, String clave, int estadoHttp, Map<String, Object> respuesta) {
    Idempotencia fila = new Idempotencia();
    fila.setUsuarioId(usuarioId);
    fila.setClave(clave);
    fila.setEstadoHttp(estadoHttp);
    fila.setRespuesta(respuesta);
    return fila;
  }
}
