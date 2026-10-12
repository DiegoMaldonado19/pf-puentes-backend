package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.auditoria.Auditoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.core.env.Environment;

/**
 * Con {@code ddl-auto=validate}, si una entidad no coincide con su tabla el contexto no levanta:
 * que esta clase cargue ya prueba el mapeo; los asserts dejan la intención por escrito.
 */
@DataJpaTest
class MapeoDeUsuariosTest extends PruebaIntegracion {

  @Autowired TestEntityManager em;

  @Autowired Environment entorno;

  @Test
  void elContextoLevantaValidandoElEsquemaConLasCincoEntidades() {
    assertThat(entorno.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(em.getEntityManager().getMetamodel().getEntities())
        .extracting(entidad -> entidad.getJavaType().getSimpleName())
        .contains(
            Usuario.class.getSimpleName(),
            Invitacion.class.getSimpleName(),
            TokenRenovacion.class.getSimpleName(),
            TokenVerificacion.class.getSimpleName(),
            Auditoria.class.getSimpleName());
  }
}
