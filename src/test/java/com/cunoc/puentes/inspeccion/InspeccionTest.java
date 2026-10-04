package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class InspeccionTest extends PruebaIntegracion {

  private static final UUID PUENTE = UUID.fromString("0192f5a0-0000-7000-8000-000000000001");
  private static final UUID AUTOR = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");
  private static final UUID FORMULARIO = UUID.fromString("0192f5a0-0000-7000-8000-000000000003");

  @Autowired TestEntityManager em;

  @Test
  void conservaElIdDelClienteLosDatosJsonbYElGps() {
    em.getEntityManager()
        .createNativeQuery(
            """
            INSERT INTO departamento VALUES ('09', 'Quetzaltenango');
            INSERT INTO municipio VALUES ('0901', '09', 'Quetzaltenango');
            INSERT INTO puente (id, codigo, nombre, municipio_codigo, ruta, ubicacion)
              VALUES ('%s', 'GT-09-0901-0001', 'Las Rosas', '0901', 'CA-1', 'SRID=4326;POINT(-91.518 14.8347)');
            INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol)
              VALUES ('%s', 'autor@cunoc.edu.gt', 'Autor', 'hash', 'ESTUDIANTE');
            INSERT INTO formulario_version (id, codigo, esquema) VALUES ('%s', 'SIECA-1', '{}');
            """
                .formatted(PUENTE, AUTOR, FORMULARIO))
        .executeUpdate();
    UUID idDelCliente = UUID.fromString("0192f5a0-0000-7000-8000-0000000000aa");
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.setId(idDelCliente);
    inspeccion.setPuenteId(PUENTE);
    inspeccion.setAutorId(AUTOR);
    inspeccion.setFormularioVersionId(FORMULARIO);
    inspeccion.setFechaInspeccion(LocalDate.of(2026, 10, 4));
    Map<String, Object> datos =
        Map.of("subestructura", Map.of("estribo_entrada", Map.of("material", "concreto")));
    inspeccion.setDatos(datos);
    inspeccion.setUbicacionInicio(
        new GeometryFactory(new PrecisionModel(), 4326)
            .createPoint(new Coordinate(-91.5182, 14.8349)));

    Inspeccion guardada = em.persistFlushFind(inspeccion);

    assertThat(guardada.getId()).isEqualTo(idDelCliente);
    assertThat(guardada.getEstado()).isEqualTo(EstadoInspeccion.BORRADOR);
    assertThat(guardada.getDatos()).isEqualTo(datos);
    assertThat(guardada.getUbicacionInicio().getY()).isEqualTo(14.8349);
  }
}
