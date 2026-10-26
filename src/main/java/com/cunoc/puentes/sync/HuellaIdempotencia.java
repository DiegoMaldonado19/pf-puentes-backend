package com.cunoc.puentes.sync;

/** Huella SHA-256 entregada por el integrador junto con su versión explícita. */
public record HuellaIdempotencia(int version, byte[] sha256) {

  public HuellaIdempotencia {
    if (version < 1) {
      throw new IllegalArgumentException("La versión de la huella debe ser positiva.");
    }
    if (sha256 == null || sha256.length != 32) {
      throw new IllegalArgumentException("La huella SHA-256 debe contener exactamente 32 bytes.");
    }
    sha256 = sha256.clone();
  }

  @Override
  public byte[] sha256() {
    return sha256.clone();
  }
}
