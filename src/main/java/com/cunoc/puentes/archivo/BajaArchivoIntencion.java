package com.cunoc.puentes.archivo;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import com.cunoc.puentes.sync.IntencionIdempotencia;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Evidencia durable, sin FK a la fila eliminada ni permisos retenidos. */
@Service
class BajaArchivoIntencion implements IntencionIdempotencia {
  private final JdbcClient jdbc;

  BajaArchivoIntencion(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Intento> buscar(UUID usuario, String clave) {
    return jdbc.sql(
            "select * from intento_baja_archivo where usuario_id = :usuario and clave = :clave")
        .param("usuario", usuario)
        .param("clave", clave)
        .query(Intento.class)
        .optional();
  }

  @Override
  public Optional<HuellaIdempotencia> buscarHuella(UUID usuario, String clave) {
    return buscar(usuario, clave)
        .map(i -> new HuellaIdempotencia(i.huellaVersion(), i.huellaSha256()));
  }

  void exigirDisponible(UUID archivo, UUID usuario, String clave) {
    boolean pendiente =
        jdbc.sql(
                """
        select exists(select 1 from intento_baja_archivo where archivo_id = :archivo
          and not finalizada and (usuario_id <> :usuario or clave <> :clave))
        """)
            .param("archivo", archivo)
            .param("usuario", usuario)
            .param("clave", clave)
            .query(Boolean.class)
            .single();
    if (pendiente) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "operacion-en-proceso",
          "Operación en proceso",
          "Hay una baja pendiente para ese UUID; reintenta su operación original.");
    }
  }

  void exigirSinPendiente(UUID archivo) {
    exigirDisponible(archivo, new UUID(0, 0), "");
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Intento preparar(UUID usuario, String clave, HuellaIdempotencia huella, Archivo archivo) {
    var anterior = buscar(usuario, clave);
    if (anterior.isPresent()) {
      return anterior.get();
    }
    jdbc.sql(
            """
        insert into intento_baja_archivo
          (usuario_id, clave, archivo_id, inspeccion_id, clave_objeto, clave_miniatura,
           huella_version, huella_sha256)
        values (:usuario, :clave, :archivo, :padre, :objeto, :miniatura, :version, :huella)
        """)
        .param("usuario", usuario)
        .param("clave", clave)
        .param("archivo", archivo.getId())
        .param("padre", archivo.getInspeccionId())
        .param("objeto", archivo.getClave())
        .param("miniatura", archivo.getClaveMiniatura())
        .param("version", huella.version())
        .param("huella", huella.sha256())
        .update();
    return buscar(usuario, clave).orElseThrow();
  }

  void finalizar(UUID usuario, String clave) {
    jdbc.sql(
            "update intento_baja_archivo set finalizada = true where usuario_id = :usuario and clave = :clave")
        .param("usuario", usuario)
        .param("clave", clave)
        .update();
  }

  record Intento(
      UUID usuarioId,
      String clave,
      UUID archivoId,
      UUID inspeccionId,
      String claveObjeto,
      String claveMiniatura,
      int huellaVersion,
      byte[] huellaSha256,
      boolean finalizada) {}
}
