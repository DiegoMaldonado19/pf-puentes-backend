package com.cunoc.puentes.inspeccion.puerto;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil {@code local}: deja una versión {@code ACTIVA} para poder iniciar inspecciones sin P4. No
 * es el formulario SIECA oficial. No corre en stage/prod ni en la suite (perfil por defecto).
 */
@Component
@Profile("local")
class SemillaFormularioDePrueba implements ApplicationRunner {

  static final UUID ID = UUID.fromString("0199b000-0000-7000-8000-00000000f001");
  static final String CODIGO = "SIECA-PRUEBA";
  static final String ESQUEMA = "{\"type\":\"object\"}";

  private static final Logger log = LoggerFactory.getLogger(SemillaFormularioDePrueba.class);

  private final EntityManager em;

  SemillaFormularioDePrueba(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments argumentos) {
    int insertadas =
        em.createNativeQuery(
                """
                INSERT INTO formulario_version (id, codigo, esquema, estado, publicada_en)
                SELECT CAST(:id AS uuid), :codigo, CAST(:esquema AS jsonb), 'ACTIVA', now()
                 WHERE NOT EXISTS (
                   SELECT 1 FROM formulario_version WHERE estado = 'ACTIVA')
                   AND NOT EXISTS (
                   SELECT 1 FROM formulario_version WHERE codigo = :codigo)
                """)
            .setParameter("id", ID.toString())
            .setParameter("codigo", CODIGO)
            .setParameter("esquema", ESQUEMA)
            .executeUpdate();
    if (insertadas == 0) {
      log.debug(
          "Ya hay una versión de formulario ACTIVA o existe {}: no se siembra SIECA-PRUEBA",
          CODIGO);
      return;
    }
    log.warn(
        "Versión de formulario de prueba {} creada (id {}). No es el SIECA oficial de P4",
        CODIGO,
        ID);
  }
}
