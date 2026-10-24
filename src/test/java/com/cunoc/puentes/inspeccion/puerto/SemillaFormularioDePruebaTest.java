package com.cunoc.puentes.inspeccion.puerto;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
class SemillaFormularioDePruebaTest extends PruebaIntegracion {

  @Autowired EntityManager em;

  @Test
  void insertaUnaVersionActivaSiNoHayNinguna() {
    new SemillaFormularioDePrueba(em).run(new DefaultApplicationArguments());
    em.flush();

    assertThat(conteoActivas()).isEqualTo(1);
    assertThat(
            em.createNativeQuery("select codigo from formulario_version where id = :id")
                .setParameter("id", SemillaFormularioDePrueba.ID)
                .getSingleResult())
        .isEqualTo(SemillaFormularioDePrueba.CODIGO);

    new SemillaFormularioDePrueba(em).run(new DefaultApplicationArguments());
    em.flush();
    assertThat(conteoActivas()).isEqualTo(1);
  }

  @Test
  @Sql("/inspeccion/formulario-activo.sql")
  void noDuplicaSiYaExisteUnaActiva() {
    new SemillaFormularioDePrueba(em).run(new DefaultApplicationArguments());
    em.flush();
    assertThat(conteoActivas()).isEqualTo(1);
  }

  private int conteoActivas() {
    Number n =
        (Number)
            em.createNativeQuery("select count(*) from formulario_version where estado = 'ACTIVA'")
                .getSingleResult();
    return n.intValue();
  }
}
