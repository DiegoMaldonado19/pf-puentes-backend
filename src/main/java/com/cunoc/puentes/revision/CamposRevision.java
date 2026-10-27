package com.cunoc.puentes.revision;

import com.cunoc.puentes.common.NegocioException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Solo propiedades reales; no acepta referencias libres ni atraviesa definiciones no usadas. */
final class CamposRevision {
  private CamposRevision() {}

  static List<DetalleRevisionDTO.CampoRevision> extraer(
      Map<String, Object> esquema, Map<String, Object> datos) {
    var campos = new ArrayList<DetalleRevisionDTO.CampoRevision>();
    recorrer(esquema, datos, "", campos);
    var referencias = new HashSet<String>();
    for (var campo : campos) {
      if (!referencias.add(campo.referencia()) || campo.referencia().length() > 200) {
        throw new NegocioException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "referencias-ambiguas",
            "Formulario incompatible",
            "Las referencias de campo deben ser únicas y de hasta 200 caracteres.");
      }
    }
    return List.copyOf(campos);
  }

  private static void recorrer(
      Map<?, ?> esquema, Object datos, String ruta, List<DetalleRevisionDTO.CampoRevision> campos) {
    if (esquema.get("properties") instanceof Map<?, ?> propiedades) {
      propiedades.forEach(
          (clave, nodo) -> {
            if (nodo instanceof Map<?, ?> hijo) {
              Object valor = datos instanceof Map<?, ?> mapa ? mapa.get(clave) : null;
              recorrer(hijo, valor, ruta.isEmpty() ? clave.toString() : ruta + "." + clave, campos);
            }
          });
    } else if (!ruta.isEmpty()) {
      // Un arreglo se ancla como campo completo; no se inventan índices ni referencias por fila.
      String referencia =
          esquema.get("x-elemento-ref") instanceof String ref && !ref.isBlank() ? ref : ruta;
      String titulo = esquema.get("title") instanceof String texto ? texto : ruta;
      campos.add(new DetalleRevisionDTO.CampoRevision(referencia, titulo, datos));
    }
  }
}
