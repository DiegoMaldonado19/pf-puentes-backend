package com.cunoc.puentes.archivo;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.inspeccion.EstadoInspeccion;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@DataJpaTest
@Sql("/archivo/inspecciones.sql")
class ArchivoRepositoryTest extends PruebaIntegracion {

  static final UUID PUENTE = UUID.fromString("0192f5a0-0000-7000-8000-000000000001");
  static final UUID AUTOR = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");
  static final UUID ABANDONADA = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b2");
  static final UUID PUBLICADA = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b3");
  static final UUID ELIMINADA = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b4");
  static final UUID ORDEN = UUID.fromString("0192f5a0-0000-7000-8000-0000000000c1");

  @Autowired ArchivoRepository repositorio;

  @Test
  void leeElPuenteElAutorYElEstadoDeLaInspeccionSinVerLasEliminadas() {
    assertThat(repositorio.buscarInspeccion(PUBLICADA))
        .hasValueSatisfying(
            inspeccion -> {
              assertThat(inspeccion.getPuenteId()).isEqualTo(PUENTE);
              assertThat(inspeccion.getAutorId()).isEqualTo(AUTOR);
              assertThat(inspeccion.getEstado()).isEqualTo(EstadoInspeccion.PUBLICADA);
            });
    assertThat(repositorio.buscarInspeccion(ELIMINADA)).isEmpty();
  }

  @Test
  void cuentaLasFotosYLosDocumentosPorSeparado() {
    assertThat(
            repositorio.countByInspeccionIdAndTipoIn(
                ABANDONADA, EnumSet.of(TipoArchivo.JPEG, TipoArchivo.WEBP)))
        .isEqualTo(1);
    assertThat(repositorio.countByInspeccionIdAndTipoIn(ABANDONADA, EnumSet.of(TipoArchivo.PDF)))
        .isEqualTo(1);
  }

  @Test
  void noCuentaLasFotosPurgadas() {
    Archivo purgada = new Archivo();
    purgada.setId(UUID.randomUUID());
    purgada.setInspeccionId(ABANDONADA);
    purgada.setTipo(TipoArchivo.JPEG);
    purgada.setClave("prueba/purgada.jpg");
    purgada.setClaveMiniatura("prueba/purgada-miniatura.jpg");
    purgada.setTamano(10);
    purgada.setPurgadoEn(Instant.now());
    repositorio.save(purgada);

    assertThat(
            repositorio.countByInspeccionIdAndTipoInAndPurgadoEnIsNull(
                ABANDONADA, EnumSet.of(TipoArchivo.JPEG, TipoArchivo.WEBP)))
        .isEqualTo(1);
  }

  @Test
  @SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
  @Sql(
      statements =
          """
          INSERT INTO orden_mantenimiento (id, puente_id, tipo, descripcion, prioridad)
            VALUES ('0192f5a0-0000-7000-8000-0000000000c1', '0192f5a0-0000-7000-8000-000000000001',
              'CORRECTIVO', 'Reparar el estribo', 'ALTA');
          """)
  void leeElPuenteDeLaOrden() {
    assertThat(repositorio.buscarPuenteDeOrden(ORDEN)).hasValue(PUENTE);
    assertThat(repositorio.buscarPuenteDeOrden(UUID.randomUUID())).isEmpty();
  }
}
