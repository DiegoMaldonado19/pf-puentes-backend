package com.cunoc.puentes.archivo;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.inspeccion.EstadoInspeccion;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Sql("/archivo/inspecciones.sql")
class ArchivoRepositoryTest extends PruebaIntegracion {

  static final UUID PUENTE = UUID.fromString("0192f5a0-0000-7000-8000-000000000001");
  static final UUID AUTOR = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");
  static final UUID ABANDONADA = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b2");
  static final UUID PUBLICADA = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b3");
  static final UUID ELIMINADA = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b4");

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
}
