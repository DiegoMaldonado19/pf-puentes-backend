package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.sync.Idempotencia;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** Valida que una fila JSONB heredada contenga una respuesta reproducible por los handlers P5. */
final class RespuestaIdempotenteInspeccion {

  private static final Set<String> CAMPOS_P5 =
      Set.of(
          "id",
          "puente",
          "formulario",
          "estado",
          "version",
          "gps_disponible",
          "datos",
          "acompanantes",
          "superada",
          "advertencias");

  private RespuestaIdempotenteInspeccion() {}

  static InspeccionDTO leer(Idempotencia fila, JsonMapper json) {
    Object respuesta = fila.getRespuesta();
    if (fila.getEstadoHttp() != HttpStatus.OK.value()
        || fila.getHuellaVersion() != null
        || fila.getHuellaSha256() != null
        || fila.getRespuestaBytes() != null
        || !(respuesta instanceof Map<?, ?> mapa)
        || !mapa.keySet().containsAll(CAMPOS_P5)) {
      throw claveIncompatible();
    }

    InspeccionDTO dto;
    try {
      dto = json.convertValue(mapa, InspeccionDTO.class);
    } catch (RuntimeException error) {
      throw claveIncompatible();
    }
    if (dto == null
        || dto.id() == null
        || dto.puente() == null
        || dto.formulario() == null
        || dto.estado() == null
        || dto.version() == null
        || dto.datos() == null
        || dto.acompanantes() == null
        || dto.advertencias() == null) {
      throw claveIncompatible();
    }
    return dto;
  }

  private static NegocioException claveIncompatible() {
    return new NegocioException(
        HttpStatus.CONFLICT,
        "clave-idempotencia-incompatible",
        "Clave de idempotencia incompatible",
        "La clave ya está asociada a otra huella o a un resultado heredado no verificable.");
  }
}
