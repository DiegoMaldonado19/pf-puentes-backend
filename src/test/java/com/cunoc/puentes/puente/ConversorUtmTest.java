package com.cunoc.puentes.puente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.cunoc.puentes.puente.dto.UtmDTO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Valores esperados: PostGIS ST_Transform a EPSG:32615 y EPSG:32616 (RN-INV-05). */
class ConversorUtmTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "Quetzaltenango, 14.8347, -91.5180, 15, 659463.359, 1640571.317",
    "Ciudad de Guatemala, 14.6349, -90.5069, 15, 768550.660, 1619421.843",
    "Tecún Umán, 14.6667, -92.1453, 15, 592030.411, 1621635.879",
    "Borde oeste del meridiano 90, 15.0, -90.000001, 15, 822650.854, 1660513.685",
    "Flores, 16.9256, -89.8936, 16, 191765.016, 1873591.920",
    "Puerto Barrios, 15.7278, -88.5944, 16, 329164.671, 1739470.946"
  })
  void coincideConPostgisAlCentimetroYEligeLaZonaPorLongitud(
      String lugar, double lat, double lon, int zona, double este, double norte) {
    UtmDTO utm = ConversorUtm.aUtm(lat, lon);

    assertThat(utm.zona()).isEqualTo(zona);
    assertThat(utm.hemisferio()).isEqualTo("N");
    assertThat(utm.este()).isCloseTo(este, within(0.01));
    assertThat(utm.norte()).isCloseTo(norte, within(0.01));
  }

  @ParameterizedTest
  @CsvSource({"-33.45, -70.66, 19, S", "14.0, -90.0, 16, N"})
  void cubreHemisferioSurYElLimiteExactoDeZona(
      double lat, double lon, int zona, String hemisferio) {
    UtmDTO utm = ConversorUtm.aUtm(lat, lon);

    assertThat(utm.zona()).isEqualTo(zona);
    assertThat(utm.hemisferio()).isEqualTo(hemisferio);
    assertThat(utm.norte()).isPositive();
  }
}
