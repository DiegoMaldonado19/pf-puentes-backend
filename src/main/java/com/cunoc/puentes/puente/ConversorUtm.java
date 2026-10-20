package com.cunoc.puentes.puente;

import com.cunoc.puentes.puente.dto.UtmDTO;

/**
 * RN-INV-05: WGS84 a UTM con las series de Snyder (USGS PP 1395). Guatemala cae en las zonas 15N y
 * 16N; la zona sale de la longitud. Error menor a 1 mm frente a PostGIS ST_Transform.
 */
final class ConversorUtm {

  private static final double A = 6_378_137.0;
  private static final double F = 1 / 298.257223563;
  private static final double K0 = 0.9996;
  private static final double E2 = F * (2 - F);
  private static final double E4 = E2 * E2;
  private static final double E6 = E4 * E2;
  private static final double EP2 = E2 / (1 - E2);
  private static final double FALSO_ESTE = 500_000;
  private static final double FALSO_NORTE_SUR = 10_000_000;

  private ConversorUtm() {}

  static UtmDTO aUtm(double latitud, double longitud) {
    int zona = (int) Math.floor((longitud + 180) / 6) + 1;
    double meridianoCentral = Math.toRadians((zona - 1) * 6 - 180 + 3);
    double phi = Math.toRadians(latitud);
    double sen = Math.sin(phi);
    double cos = Math.cos(phi);
    double tan = Math.tan(phi);

    double n = A / Math.sqrt(1 - E2 * sen * sen);
    double t = tan * tan;
    double c = EP2 * cos * cos;
    double a = cos * (Math.toRadians(longitud) - meridianoCentral);
    double m =
        A
            * ((1 - E2 / 4 - 3 * E4 / 64 - 5 * E6 / 256) * phi
                - (3 * E2 / 8 + 3 * E4 / 32 + 45 * E6 / 1024) * Math.sin(2 * phi)
                + (15 * E4 / 256 + 45 * E6 / 1024) * Math.sin(4 * phi)
                - 35 * E6 / 3072 * Math.sin(6 * phi));

    double este =
        K0
                * n
                * (a
                    + (1 - t + c) * Math.pow(a, 3) / 6
                    + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * Math.pow(a, 5) / 120)
            + FALSO_ESTE;
    double norte =
        K0
            * (m
                + n
                    * tan
                    * (a * a / 2
                        + (5 - t + 9 * c + 4 * c * c) * Math.pow(a, 4) / 24
                        + (61 - 58 * t + t * t + 600 * c - 330 * EP2) * Math.pow(a, 6) / 720));
    boolean sur = latitud < 0;
    return new UtmDTO(
        zona,
        sur ? "S" : "N",
        centimetros(este),
        centimetros(sur ? norte + FALSO_NORTE_SUR : norte));
  }

  private static double centimetros(double metros) {
    return Math.round(metros * 100) / 100.0;
  }
}
