package com.cunoc.puentes.puente;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class PuenteTest extends PruebaIntegracion {

  @Autowired TestEntityManager em;

  @Test
  void generaUuidV7YGuardaLaUbicacionComoGeography() {
    em.getEntityManager()
        .createNativeQuery(
            """
            INSERT INTO departamento VALUES ('09', 'Quetzaltenango');
            INSERT INTO municipio VALUES ('0901', '09', 'Quetzaltenango');
            """)
        .executeUpdate();
    Puente puente = new Puente();
    puente.setCodigo("GT-09-0901-0001");
    puente.setNombre("Puente Las Rosas");
    puente.setMunicipio(em.find(Municipio.class, "0901"));
    puente.setRuta("CA-1");
    puente.setUbicacion(
        new GeometryFactory(new PrecisionModel(), 4326)
            .createPoint(new Coordinate(-91.5180, 14.8347)));

    Puente guardado = em.persistFlushFind(puente);

    assertThat(guardado.getId().version()).isEqualTo(7);
    assertThat(guardado.getCreadoEn()).isNotNull();
    assertThat(guardado.getUbicacion().getX()).isEqualTo(-91.5180);
    assertThat(guardado.getUbicacion().getY()).isEqualTo(14.8347);
    assertThat(guardado.getMunicipio().getDepartamento().getNombre()).isEqualTo("Quetzaltenango");
    assertThat(guardado.getEstadoActual()).isNull(); // RN-INV-10: Sin evaluar
  }
}
