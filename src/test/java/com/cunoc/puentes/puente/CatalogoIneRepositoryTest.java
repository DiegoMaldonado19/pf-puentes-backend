package com.cunoc.puentes.puente;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** El catálogo INE lo carga la migración V2026_10_20_0130 (RN-INV-03, RN-INV-04). */
@DataJpaTest
class CatalogoIneRepositoryTest extends PruebaIntegracion {

  @Autowired DepartamentoRepository departamentos;
  @Autowired MunicipioRepository municipios;

  @Test
  void laMigracionCargaLos22DepartamentosOrdenadosPorNombre() {
    var lista = departamentos.findAllByOrderByNombreAsc();

    // otras suites agregan departamentos ficticios (98, 99) a la base compartida
    assertThat(lista).filteredOn(d -> Integer.parseInt(d.getCodigo()) <= 22).hasSize(22);
    assertThat(lista.getFirst().getNombre()).isEqualTo("Alta Verapaz");
  }

  @Test
  void cadaMunicipioPerteneceASuDepartamento() {
    var deQuetzaltenango = municipios.findByDepartamentoCodigoOrderByNombreAsc("09");

    assertThat(deQuetzaltenango).hasSize(24);
    assertThat(deQuetzaltenango)
        .allSatisfy(m -> assertThat(m.getDepartamento().getCodigo()).isEqualTo("09"));
    assertThat(municipios.findById("0901")).map(Municipio::getNombre).hasValue("Quetzaltenango");
  }
}
