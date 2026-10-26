package com.cunoc.puentes.sync;

/** Status y cuerpo binario exactos que el integrador admite para guardar y reproducir. */
public record RespuestaIdempotente(int estadoHttp, byte[] cuerpo) {

  public RespuestaIdempotente {
    if (estadoHttp < 100 || estadoHttp > 599) {
      throw new IllegalArgumentException("El estado HTTP debe estar entre 100 y 599.");
    }
    if (cuerpo == null) {
      throw new IllegalArgumentException("El cuerpo de la respuesta no puede ser nulo.");
    }
    cuerpo = cuerpo.clone();
  }

  @Override
  public byte[] cuerpo() {
    return cuerpo.clone();
  }
}
