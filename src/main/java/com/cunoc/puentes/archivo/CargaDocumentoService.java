package com.cunoc.puentes.archivo;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import com.cunoc.puentes.sync.IntencionIdempotencia;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** La intención se confirma antes de MinIO y sobrevive al rollback de dominio/resultado. */
@Service
class CargaDocumentoService implements IntencionIdempotencia {
  private final JdbcClient jdbc;
  private final AlmacenamientoArchivos almacenamiento;

  CargaDocumentoService(JdbcClient jdbc, AlmacenamientoArchivos almacenamiento) {
    this.jdbc = jdbc;
    this.almacenamiento = almacenamiento;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Intento> preparar(
      UUID usuario,
      String clave,
      HuellaIdempotencia huella,
      UUID archivo,
      UUID padre,
      String carpeta,
      byte[] contenido) {
    if (clave != null) {
      exigirHuella(usuario, clave, huella);
      Optional<Intento> anterior = buscar(usuario, clave);
      if (anterior.isPresent()) {
        return anterior;
      }
    }
    exigirSinReservaPendiente(archivo);
    if (clave == null) {
      return Optional.empty();
    }
    String objeto = carpeta + UUID.randomUUID() + ".pdf";
    jdbc.sql(
            """
        insert into intento_documento (usuario_id, clave, huella_version, huella_sha256,
          archivo_id, inspeccion_id, clave_objeto, contenido_sha256, tamano)
        values (:usuario, :clave, :version, :huella, :archivo, :padre, :objeto, :contenido, :tamano)
        """)
        .param("usuario", usuario)
        .param("clave", clave)
        .param("version", huella.version())
        .param("huella", huella.sha256())
        .param("archivo", archivo)
        .param("padre", padre)
        .param("objeto", objeto)
        .param("contenido", HuellaDocumento.sha256(contenido))
        .param("tamano", contenido.length)
        .update();
    return buscar(usuario, clave);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Intento prepararFoto(
      UUID usuario,
      String clave,
      HuellaIdempotencia huella,
      UUID archivo,
      UUID padre,
      String carpeta,
      byte[] foto,
      TipoArchivo tipo,
      byte[] miniatura,
      TipoArchivo tipoMiniatura) {
    exigirHuella(usuario, clave, huella);
    var anterior = buscar(usuario, clave);
    if (anterior.isPresent()) {
      return anterior.get();
    }
    exigirSinReservaPendiente(archivo);
    String nombre = carpeta + UUID.randomUUID();
    jdbc.sql(
            """
        insert into intento_documento (usuario_id, clave, huella_version, huella_sha256,
          archivo_id, inspeccion_id, clave_objeto, contenido_sha256, tamano,
          tipo_mime, clave_miniatura, miniatura_sha256, miniatura_tamano, miniatura_mime)
        values (:usuario, :clave, :version, :huella, :archivo, :padre, :objeto, :contenido,
          :tamano, :mime, :miniatura, :miniHash, :miniTamano, :miniMime)
        """)
        .param("usuario", usuario)
        .param("clave", clave)
        .param("version", huella.version())
        .param("huella", huella.sha256())
        .param("archivo", archivo)
        .param("padre", padre)
        .param("objeto", nombre + "." + tipo.extension)
        .param("contenido", HuellaDocumento.sha256(foto))
        .param("tamano", foto.length)
        .param("mime", tipo.mime)
        .param("miniatura", nombre + "-miniatura." + tipoMiniatura.extension)
        .param("miniHash", HuellaDocumento.sha256(miniatura))
        .param("miniTamano", miniatura.length)
        .param("miniMime", tipoMiniatura.mime)
        .update();
    return buscar(usuario, clave).orElseThrow();
  }

  void reconciliarFoto(Intento intento, byte[] foto, byte[] miniatura) {
    reconciliarObjeto(
        intento.claveObjeto(),
        foto,
        new AlmacenamientoArchivos.Integridad(
            intento.tamano(), intento.tipoMime(), intento.contenidoSha256()));
    reconciliarObjeto(
        intento.claveMiniatura(),
        miniatura,
        new AlmacenamientoArchivos.Integridad(
            intento.miniaturaTamano(), intento.miniaturaMime(), intento.miniaturaSha256()));
  }

  /** Bajo el bloqueo UUID común, antes de crear objetos de cualquier tipo o padre. */
  void exigirSinReservaPendiente(UUID archivo) {
    boolean pendiente =
        jdbc.sql(
                "select exists(select 1 from intento_documento where archivo_id = :id and not finalizada)")
            .param("id", archivo)
            .query(Boolean.class)
            .single();
    if (pendiente) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "operacion-en-proceso",
          "Operación en proceso",
          "Hay una carga pendiente para ese UUID; reintenta su operación original.");
    }
  }

  @Override
  public Optional<HuellaIdempotencia> buscarHuella(UUID usuarioId, String clave) {
    // Solo lectura: el ejecutor ya posee el bloqueo usuario/clave en su conexión exterior.
    return buscar(usuarioId, clave)
        .map(intento -> new HuellaIdempotencia(intento.huellaVersion(), intento.huellaSha256()));
  }

  void exigirHuella(UUID usuario, String clave, HuellaIdempotencia huella) {
    buscar(usuario, clave)
        .ifPresent(
            intento -> {
              if (intento.huellaVersion() != huella.version()
                  || !MessageDigest.isEqual(intento.huellaSha256(), huella.sha256())) {
                throw incompatible();
              }
            });
  }

  void reconciliar(Intento intento, byte[] contenido) {
    var esperado =
        new AlmacenamientoArchivos.Integridad(
            intento.tamano(), TipoArchivo.PDF.mime, intento.contenidoSha256());
    reconciliarObjeto(intento.claveObjeto(), contenido, esperado);
  }

  private void reconciliarObjeto(
      String clave, byte[] contenido, AlmacenamientoArchivos.Integridad esperado) {
    var actual = almacenamiento.inspeccionar(clave);
    if (actual.isEmpty()) {
      almacenamiento.guardar(clave, contenido, esperado.tipoMime());
      actual = almacenamiento.inspeccionar(clave);
    }
    if (actual.isEmpty()) {
      throw new IllegalStateException("El PDF no existe después de guardarlo.");
    }
    if (!esperado.equals(actual.get())) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "objeto-archivo-incompatible",
          "Objeto de archivo incompatible",
          "El contenido remoto no coincide con la intención de carga; no se sobrescribió.");
    }
  }

  void finalizar(Intento intento) {
    jdbc.sql(
            "update intento_documento set finalizada = true where usuario_id = :usuario and clave = :clave")
        .param("usuario", intento.usuarioId())
        .param("clave", intento.clave())
        .update();
  }

  private Optional<Intento> buscar(UUID usuario, String clave) {
    return jdbc.sql(
            "select * from intento_documento where usuario_id = :usuario and clave = :clave")
        .param("usuario", usuario)
        .param("clave", clave)
        .query(Intento.class)
        .optional();
  }

  private static NegocioException incompatible() {
    return new NegocioException(
        HttpStatus.CONFLICT,
        "clave-idempotencia-incompatible",
        "Clave de idempotencia incompatible",
        "La clave ya está asociada a otra huella o a un resultado heredado no verificable.");
  }

  record Intento(
      UUID usuarioId,
      String clave,
      int huellaVersion,
      byte[] huellaSha256,
      UUID archivoId,
      UUID inspeccionId,
      String claveObjeto,
      String contenidoSha256,
      long tamano,
      boolean finalizada,
      java.time.Instant creadoEn,
      String tipoMime,
      String claveMiniatura,
      String miniaturaSha256,
      Long miniaturaTamano,
      String miniaturaMime) {}
}
