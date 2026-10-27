package com.cunoc.puentes.archivo;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Orden común: inspección SQL, luego UUID de archivo, hasta confirmar o revertir. */
@Component
class BloqueoArchivo {
  private final JdbcClient jdbc;

  BloqueoArchivo(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void inspeccion(UUID id) {
    jdbc.sql("select id from inspeccion where id = :id for update")
        .param("id", id)
        .query(UUID.class)
        .optional();
  }

  void archivo(UUID padre, UUID archivo) {
    inspeccion(padre);
    uuid(archivo);
  }

  void uuid(UUID archivo) {
    jdbc.sql("select pg_advisory_xact_lock(hashtextextended('archivo:' || cast(:id as text), 0))")
        .param("id", archivo)
        .query(Object.class)
        .single();
  }

  void baja(UUID archivo) {
    jdbc.sql("select inspeccion_id from archivo where id = :id and inspeccion_id is not null")
        .param("id", archivo)
        .query(UUID.class)
        .optional()
        .ifPresent(padre -> archivo(padre, archivo));
  }
}
