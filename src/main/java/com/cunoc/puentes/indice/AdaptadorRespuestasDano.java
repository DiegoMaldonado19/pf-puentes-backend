package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/** Prepara entradas del núcleo existente; no calcula estados ni deduce condiciones críticas. */
final class AdaptadorRespuestasDano {
  List<CalificacionElemento> adaptar(
      Map<String, CampoDeDano> campos, JuegoPesos juego, Map<String, Object> datos) {
    if (campos == null || juego == null || datos == null) {
      throw new IllegalArgumentException("Campos, juego y respuestas son obligatorios.");
    }
    var referencias =
        juego.pesos().stream().map(PesoConfigurado::elementoRef).collect(Collectors.toSet());
    if (!referencias.equals(campos.keySet())) {
      throw invalido(
          "juego-pesos-incompatible",
          "pesos",
          "El juego debe cubrir exactamente los campos de daño de esta versión.");
    }
    return juego.pesos().stream()
        .sorted(Comparator.comparing(PesoConfigurado::elementoRef))
        .map(peso -> calificacion(campos.get(peso.elementoRef()), peso, datos))
        .toList();
  }

  private CalificacionElemento calificacion(
      CampoDeDano campo, PesoConfigurado peso, Map<String, Object> datos) {
    String ruta = String.join(".", campo.ruta());
    if (campo.repetido()) {
      throw invalido(
          "dano-repetido-no-soportado",
          ruta,
          "Los daños dentro de arreglos requieren una política de agregación explícita.");
    }
    Object valor = datos;
    for (String propiedad : campo.ruta()) {
      if (!(valor instanceof Map<?, ?> objeto) || !objeto.containsKey(propiedad)) {
        throw invalido(
            "respuesta-dano-invalida",
            ruta,
            "Falta la calificación de daño en su ruta real; no se sustituye por cero.");
      }
      valor = objeto.get(propiedad);
    }
    if (valor instanceof Number numero) {
      try {
        return new CalificacionElemento(
            new BigDecimal(numero.toString()).intValueExact(), peso.peso());
      } catch (ArithmeticException | IllegalArgumentException error) {
        throw valorInvalido(ruta);
      }
    }
    throw valorInvalido(ruta);
  }

  private NegocioException valorInvalido(String ruta) {
    return invalido(
        "respuesta-dano-invalida",
        ruta,
        "La calificación debe ser un entero entre 0 y 4; no admite null ni no aplicable.");
  }

  private NegocioException invalido(String tipo, String ruta, String detalle) {
    return new NegocioException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            tipo,
            "No se pueden preparar las calificaciones de daño",
            detalle)
        .conErrores(List.of(new ErrorCampo(ruta, detalle)));
  }
}
