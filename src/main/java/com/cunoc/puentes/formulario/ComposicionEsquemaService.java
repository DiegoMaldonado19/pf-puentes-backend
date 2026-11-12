package com.cunoc.puentes.formulario;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.formulario.dto.CampoExtraDTO;
import com.cunoc.puentes.formulario.dto.EsquemaBaseDTO;
import com.cunoc.puentes.formulario.dto.SeccionFormularioDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Compone el esquema de una versión nueva: el esquema base SIECA más los campos adicionales que
 * elige el administrador (B2).
 *
 * <p>Los campos adicionales no pueden alterar lo que el motor interpreta para el índice de
 * condición, los pesos, la precarga ni el catálogo: se construyen solo desde atributos tipados, de
 * modo que nunca aparecen extensiones como {@code x-dano}, {@code x-permanente}, {@code x-grupo},
 * {@code x-anulacion-critica} o {@code x-elemento-ref}; además, las secciones {@code
 * identificacion} y {@code tramos} están cerradas. Así el índice, los pesos y la precarga no
 * cambian (ADR 0058).
 */
@Service
public class ComposicionEsquemaService {

  static final String RECURSO_BASE = "formulario/formulario-sieca-v1.json";
  static final int MAXIMO_CAMPOS_EXTRA = 40;
  static final Set<String> SECCIONES_CERRADAS = Set.of("identificacion", "tramos");
  static final Set<String> TIPOS =
      Set.of("texto", "texto_largo", "numero", "entero", "si_no", "opcion", "fecha");

  private static final Pattern CLAVE = Pattern.compile("^[a-z][a-z0-9_]{2,40}$");
  private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};
  private static final int MAXIMO_TITULO = 120;
  private static final int MAXIMO_AYUDA = 300;
  private static final int MAXIMO_UNIDAD = 20;
  private static final int MAXIMO_OPCIONES = 50;
  private static final int MAXIMO_OPCION = 120;
  private static final int MAXIMO_TEXTO = 500;
  private static final int MAXIMO_TEXTO_LARGO = 4000;

  // Mapper propio, como FormularioValidacionServiceImpl: el contexto no expone el de Jackson 2.
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, Object> base = cargarBase(mapper);

  /** Esquema base (copia) y secciones; las secciones cerradas se marcan para la interfaz. */
  public EsquemaBaseDTO esquemaBase() {
    return new EsquemaBaseDTO(copiaBase(), secciones());
  }

  /** Errores de los campos adicionales, con la ruta {@code campos_extra[i].atributo}. */
  public List<ErrorCampo> validar(List<CampoExtraDTO> extras) {
    List<CampoExtraDTO> lista = extras == null ? List.of() : extras;
    List<ErrorCampo> errores = new ArrayList<>();
    if (lista.size() > MAXIMO_CAMPOS_EXTRA) {
      errores.add(
          new ErrorCampo(
              "campos_extra",
              "Un formulario admite como máximo %d campos adicionales."
                  .formatted(MAXIMO_CAMPOS_EXTRA)));
    }
    Set<String> ocupadas = new HashSet<>();
    for (int i = 0; i < lista.size(); i++) {
      validarCampo(lista.get(i), "campos_extra[" + i + "]", ocupadas, errores);
    }
    return errores;
  }

  /**
   * Base más campos adicionales, marcados con {@code x-extra}. Los obligatorios entran en la lista
   * {@code required} de su sección.
   *
   * @throws NegocioException 422 con los errores por campo si la composición no es válida
   */
  public Map<String, Object> componer(List<CampoExtraDTO> extras) {
    List<CampoExtraDTO> lista = extras == null ? List.of() : extras;
    List<ErrorCampo> errores = validar(lista);
    if (!errores.isEmpty()) {
      throw new NegocioException(
              HttpStatus.UNPROCESSABLE_CONTENT,
              "campos-extra-invalidos",
              "Campos adicionales no válidos",
              "Corrija los campos marcados antes de guardar el formulario.")
          .conErrores(errores);
    }
    Map<String, Object> esquema = copiaBase();
    Map<String, Object> secciones = mapa(esquema.get("properties"));
    for (CampoExtraDTO campo : lista) {
      Map<String, Object> seccion = mapa(secciones.get(campo.seccion()));
      Map<String, Object> campos = mapa(seccion.get("properties"));
      campos.put(campo.clave(), definicion(campo, siguienteOrden(campos)));
      if (Boolean.TRUE.equals(campo.obligatorio())) {
        requeridos(seccion).add(campo.clave());
      }
    }
    return esquema;
  }

  private void validarCampo(
      CampoExtraDTO campo, String ruta, Set<String> ocupadas, List<ErrorCampo> errores) {
    if (campo == null) {
      errores.add(new ErrorCampo(ruta, "El campo adicional está vacío."));
      return;
    }
    validarSeccion(campo.seccion(), ruta, errores);
    validarClave(campo.seccion(), campo.clave(), ruta, ocupadas, errores);
    validarTitulo(campo.titulo(), ruta, errores);
    validarTipo(campo, ruta, errores);
    if (longitud(campo.ayuda()) > MAXIMO_AYUDA) {
      errores.add(
          new ErrorCampo(
              ruta + ".ayuda",
              "La ayuda admite como máximo %d caracteres.".formatted(MAXIMO_AYUDA)));
    }
  }

  private void validarSeccion(String seccion, String ruta, List<ErrorCampo> errores) {
    String atributo = ruta + ".seccion";
    if (longitud(seccion) == 0) {
      errores.add(new ErrorCampo(atributo, "Indique la sección del campo."));
    } else if (!mapa(base.get("properties")).containsKey(seccion)) {
      errores.add(new ErrorCampo(atributo, "La sección indicada no existe en el formulario base."));
    } else if (SECCIONES_CERRADAS.contains(seccion)) {
      errores.add(new ErrorCampo(atributo, "Esta sección no admite campos adicionales."));
    }
  }

  private void validarClave(
      String seccion, String clave, String ruta, Set<String> ocupadas, List<ErrorCampo> errores) {
    String atributo = ruta + ".clave";
    if (clave == null || !CLAVE.matcher(clave).matches()) {
      errores.add(
          new ErrorCampo(
              atributo,
              "La clave solo admite minúsculas, números y guion bajo, empieza por letra y tiene"
                  + " entre 3 y 41 caracteres."));
    } else if (existeEnBase(seccion, clave) || !ocupadas.add(seccion + "." + clave)) {
      errores.add(new ErrorCampo(atributo, "Ya existe un campo con esa clave en la sección."));
    }
  }

  private static void validarTitulo(String titulo, String ruta, List<ErrorCampo> errores) {
    String atributo = ruta + ".titulo";
    if (longitud(titulo) == 0) {
      errores.add(new ErrorCampo(atributo, "Escriba un título para el campo."));
    } else if (longitud(titulo) > MAXIMO_TITULO) {
      errores.add(
          new ErrorCampo(
              atributo, "El título admite como máximo %d caracteres.".formatted(MAXIMO_TITULO)));
    }
  }

  private static void validarTipo(CampoExtraDTO campo, String ruta, List<ErrorCampo> errores) {
    if (campo.tipo() == null || !TIPOS.contains(campo.tipo())) {
      errores.add(new ErrorCampo(ruta + ".tipo", "Elija un tipo de campo válido."));
      return;
    }
    validarOpciones(campo, ruta, errores);
    validarLimites(campo, ruta, errores);
    validarUnidad(campo, ruta, errores);
  }

  private static void validarOpciones(CampoExtraDTO campo, String ruta, List<ErrorCampo> errores) {
    String atributo = ruta + ".opciones";
    List<String> opciones = campo.opciones() == null ? List.of() : campo.opciones();
    if (!"opcion".equals(campo.tipo())) {
      if (!opciones.isEmpty()) {
        errores.add(new ErrorCampo(atributo, "Las opciones solo aplican a campos de tipo opción."));
      }
    } else if (opciones.isEmpty()) {
      errores.add(new ErrorCampo(atributo, "Un campo de opción necesita al menos una opción."));
    } else if (opciones.size() > MAXIMO_OPCIONES) {
      errores.add(
          new ErrorCampo(
              atributo,
              "Un campo de opción admite como máximo %d opciones.".formatted(MAXIMO_OPCIONES)));
    } else if (opcionesInvalidas(opciones)) {
      errores.add(
          new ErrorCampo(
              atributo,
              "Cada opción tiene entre 1 y %d caracteres y no puede repetirse."
                  .formatted(MAXIMO_OPCION)));
    }
  }

  private static boolean opcionesInvalidas(List<String> opciones) {
    boolean vacia = opciones.stream().anyMatch(opcion -> longitud(opcion) == 0);
    boolean muyLarga = opciones.stream().anyMatch(opcion -> longitud(opcion) > MAXIMO_OPCION);
    long distintas =
        opciones.stream().filter(opcion -> opcion != null).map(String::trim).distinct().count();
    return vacia || muyLarga || distintas != opciones.size();
  }

  private static void validarLimites(CampoExtraDTO campo, String ruta, List<ErrorCampo> errores) {
    String atributo = ruta + ".minimo";
    if (!esNumerico(campo.tipo())) {
      if (campo.minimo() != null || campo.maximo() != null) {
        errores.add(new ErrorCampo(atributo, "Los límites solo aplican a campos numéricos."));
      }
      return;
    }
    if (campo.minimo() != null
        && campo.maximo() != null
        && campo.minimo().compareTo(campo.maximo()) > 0) {
      errores.add(
          new ErrorCampo(ruta + ".maximo", "El máximo debe ser mayor o igual que el mínimo."));
    }
    if ("entero".equals(campo.tipo())
        && (noEsEntero(campo.minimo()) || noEsEntero(campo.maximo()))) {
      errores.add(
          new ErrorCampo(atributo, "Los límites de un campo entero deben ser números enteros."));
    }
  }

  private static void validarUnidad(CampoExtraDTO campo, String ruta, List<ErrorCampo> errores) {
    if (longitud(campo.unidad()) == 0) {
      return;
    }
    if (!esNumerico(campo.tipo()) || longitud(campo.unidad()) > MAXIMO_UNIDAD) {
      errores.add(
          new ErrorCampo(
              ruta + ".unidad",
              "La unidad solo aplica a campos numéricos y admite hasta %d caracteres."
                  .formatted(MAXIMO_UNIDAD)));
    }
  }

  private static Map<String, Object> definicion(CampoExtraDTO campo, int orden) {
    Map<String, Object> definicion = new LinkedHashMap<>();
    definicion.put("title", campo.titulo().trim());
    if (longitud(campo.ayuda()) > 0) {
      definicion.put("description", campo.ayuda().trim());
    }
    definicion.putAll(tipo(campo));
    if (longitud(campo.unidad()) > 0) {
      definicion.put("x-unidad", campo.unidad().trim());
    }
    definicion.put("x-extra", true);
    definicion.put("x-orden", orden);
    return definicion;
  }

  private static Map<String, Object> tipo(CampoExtraDTO campo) {
    Map<String, Object> tipo = new LinkedHashMap<>();
    switch (campo.tipo()) {
      case "texto" -> {
        tipo.put("type", "string");
        tipo.put("maxLength", MAXIMO_TEXTO);
      }
      case "texto_largo" -> {
        tipo.put("type", "string");
        tipo.put("maxLength", MAXIMO_TEXTO_LARGO);
        tipo.put("x-control", "textarea");
      }
      case "numero", "entero" -> {
        tipo.put("type", "numero".equals(campo.tipo()) ? "number" : "integer");
        if (campo.minimo() != null) {
          tipo.put("minimum", campo.minimo());
        }
        if (campo.maximo() != null) {
          tipo.put("maximum", campo.maximo());
        }
      }
      case "si_no" -> tipo.put("type", "boolean");
      case "opcion" -> {
        tipo.put("type", "string");
        tipo.put("enum", campo.opciones().stream().map(String::trim).toList());
      }
      default -> {
        tipo.put("type", "string");
        tipo.put("format", "date");
      }
    }
    return tipo;
  }

  private boolean existeEnBase(String seccion, String clave) {
    Map<String, Object> secciones = mapa(base.get("properties"));
    return secciones.containsKey(seccion)
        && mapa(mapa(secciones.get(seccion)).get("properties")).containsKey(clave);
  }

  private List<SeccionFormularioDTO> secciones() {
    return mapa(base.get("properties")).entrySet().stream()
        .map(
            seccion ->
                new SeccionFormularioDTO(
                    seccion.getKey(),
                    String.valueOf(mapa(seccion.getValue()).get("title")),
                    !SECCIONES_CERRADAS.contains(seccion.getKey())))
        .toList();
  }

  private Map<String, Object> copiaBase() {
    return mapper.convertValue(base, MAPA);
  }

  private static int siguienteOrden(Map<String, Object> campos) {
    return campos.values().stream()
            .map(campo -> mapa(campo).get("x-orden"))
            .filter(Number.class::isInstance)
            .mapToInt(orden -> ((Number) orden).intValue())
            .max()
            .orElse(0)
        + 1;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> requeridos(Map<String, Object> seccion) {
    return (List<Object>) seccion.computeIfAbsent("required", clave -> new ArrayList<>());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapa(Object valor) {
    // Las secciones de arreglo (tramos) no tienen properties en el primer nivel
    return valor instanceof Map<?, ?> ? (Map<String, Object>) valor : Map.of();
  }

  private static boolean esNumerico(String tipo) {
    return "numero".equals(tipo) || "entero".equals(tipo);
  }

  private static boolean noEsEntero(BigDecimal valor) {
    return valor != null && valor.stripTrailingZeros().scale() > 0;
  }

  private static int longitud(String texto) {
    return texto == null ? 0 : texto.trim().length();
  }

  private static Map<String, Object> cargarBase(ObjectMapper mapper) {
    try (InputStream entrada = new ClassPathResource(RECURSO_BASE).getInputStream()) {
      return mapper.readValue(entrada, MAPA);
    } catch (IOException error) {
      throw new IllegalStateException("No se pudo leer el esquema base " + RECURSO_BASE, error);
    }
  }
}
