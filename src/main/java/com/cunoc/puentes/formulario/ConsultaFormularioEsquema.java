package com.cunoc.puentes.formulario;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import com.cunoc.puentes.inspeccion.puerto.GrupoElemento;
import com.cunoc.puentes.inspeccion.puerto.RutasDatos;
import com.cunoc.puentes.inspeccion.puerto.VersionFormularioVista;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contratos 1 y 2 de P4: lo que P5 necesita del formulario sale de su JSON Schema. */
@Service
public class ConsultaFormularioEsquema implements ConsultaFormulario {

  private static final String REQUERIDO = "Requerido";

  private final FormularioVersionRepository versiones;
  private final FormularioValidacionService validacion;

  public ConsultaFormularioEsquema(
      FormularioVersionRepository versiones, FormularioValidacionService validacion) {
    this.versiones = versiones;
    this.validacion = validacion;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VersionFormularioVista> versionActiva() {
    return versiones
        .findByEstado(EstadoFormulario.ACTIVA)
        .map(
            version ->
                new VersionFormularioVista(
                    version.getId(), version.getCodigo(), version.getEsquema()));
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> rutasPermanentes(UUID versionId) {
    return rutasCon(versionId, "x-permanente");
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> rutasDeDano(UUID versionId) {
    return rutasCon(versionId, "x-dano");
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, String> rutasPorReferencia(UUID versionId) {
    Map<String, String> rutas = new LinkedHashMap<>();
    propiedades(esquema(versionId))
        .forEach(
            (ruta, propiedad) -> {
              if (propiedad.get("x-elemento-ref") instanceof String referencia) {
                rutas.put(referencia, ruta);
              }
            });
    return rutas;
  }

  @Override
  @Transactional(readOnly = true)
  public List<GrupoElemento> grupos(UUID versionId) {
    Map<String, Set<String>> porGrupo = new LinkedHashMap<>();
    propiedades(esquema(versionId))
        .forEach(
            (ruta, propiedad) -> {
              if (Boolean.TRUE.equals(propiedad.get("x-dano"))
                  && propiedad.get("x-grupo") instanceof String grupo) {
                porGrupo.computeIfAbsent(grupo, ignorado -> new LinkedHashSet<>()).add(ruta);
              }
            });
    return porGrupo.entrySet().stream()
        .map(entrada -> new GrupoElemento(entrada.getKey(), entrada.getValue()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public void validarParcial(Map<String, Object> datos, UUID versionId) {
    lanzarSiHay(
        validacion.erroresDeContenido(versionId, sinNulos(datos)),
        "Hay valores con tipo o rango inválido.");
  }

  @Override
  @Transactional(readOnly = true)
  public void validarCompleto(Map<String, Object> datos, UUID versionId) {
    Map<String, Object> contenido = sinNulos(datos);
    List<ErrorCampo> errores = new ArrayList<>();
    faltantes(esquema(versionId), "", contenido, errores);
    errores.addAll(validacion.erroresCompletos(versionId, contenido));
    lanzarSiHay(errores, "Faltan campos obligatorios o hay valores inválidos.");
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> traducir(Map<String, Object> datos, UUID origen, UUID destino) {
    Instant desde = publicadaEn(origen);
    Instant hasta = publicadaEn(destino);
    if (desde == null || hasta == null || !desde.isBefore(hasta)) {
      return datos;
    }
    Map<String, Object> traducidos = datos;
    for (FormularioVersion version :
        versiones.findByPublicadaEnGreaterThanAndPublicadaEnLessThanEqualOrderByPublicadaEnAsc(
            desde, hasta)) {
      traducidos = aplicarMapeo(traducidos, version.getMapeo());
    }
    return traducidos;
  }

  /** Mapeo {"ruta.antigua": "ruta.nueva"}; un destino que no es texto marca el campo eliminado. */
  static Map<String, Object> aplicarMapeo(Map<String, Object> datos, Map<String, Object> mapeo) {
    if (mapeo == null || mapeo.isEmpty()) {
      return datos;
    }
    Map<String, Object> resultado = RutasDatos.copiar(datos);
    mapeo.keySet().forEach(antigua -> RutasDatos.quitar(resultado, antigua));
    mapeo.forEach(
        (antigua, nueva) -> {
          Object valor = RutasDatos.leer(datos, antigua);
          if (nueva instanceof String ruta && valor != null) {
            RutasDatos.escribir(resultado, ruta, valor);
          }
        });
    return resultado;
  }

  private Instant publicadaEn(UUID versionId) {
    return versionId == null
        ? null
        : versiones.findById(versionId).map(FormularioVersion::getPublicadaEn).orElse(null);
  }

  private Map<String, Object> esquema(UUID versionId) {
    return versiones
        .findById(versionId)
        .map(FormularioVersion::getEsquema)
        .orElseThrow(
            () ->
                new NegocioException(
                    HttpStatus.NOT_FOUND,
                    "formulario-no-encontrado",
                    "Versión de formulario no encontrada",
                    "No hay una versión de formulario con ese id."));
  }

  private Set<String> rutasCon(UUID versionId, String marca) {
    return propiedades(esquema(versionId)).entrySet().stream()
        .filter(entrada -> Boolean.TRUE.equals(entrada.getValue().get(marca)))
        .map(Map.Entry::getKey)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Propiedades dentro de objetos, con su ruta de datos; los arreglos no tienen ruta fija. */
  private static Map<String, Map<String, Object>> propiedades(Map<String, Object> esquema) {
    Map<String, Map<String, Object>> planas = new LinkedHashMap<>();
    aplanar("", esquema, planas);
    return planas;
  }

  private static void aplanar(
      String ruta, Map<String, Object> nodo, Map<String, Map<String, Object>> planas) {
    mapa(nodo.get("properties"))
        .forEach(
            (nombre, hijo) -> {
              String rutaHijo = ruta.isEmpty() ? nombre : ruta + "." + nombre;
              Map<String, Object> propiedad = mapa(hijo);
              planas.put(rutaHijo, propiedad);
              aplanar(rutaHijo, propiedad, planas);
            });
  }

  /**
   * Marca cada campo obligatorio ausente. Un objeto ausente marca sus obligatorios de adentro; un
   * objeto o arreglo presente se revisa aunque el campo no sea obligatorio.
   */
  private static void faltantes(
      Map<String, Object> esquema,
      String ruta,
      Map<String, Object> datos,
      List<ErrorCampo> errores) {
    Map<String, Object> propiedades = mapa(esquema.get("properties"));
    List<String> obligatorios = requeridos(esquema);
    for (String nombre : propiedades.keySet()) {
      Map<String, Object> propiedad = mapa(propiedades.get(nombre));
      String campo = ruta + nombre;
      Object valor = datos.get(nombre);
      if (valor == null) {
        if (!obligatorios.contains(nombre)) {
          continue;
        }
        if (esObjeto(propiedad)) {
          faltantes(propiedad, campo + ".", Map.of(), errores);
        } else {
          errores.add(new ErrorCampo(campo, REQUERIDO));
        }
      } else if (valor instanceof Map<?, ?> hijo && esObjeto(propiedad)) {
        faltantes(propiedad, campo + ".", mapa(hijo), errores);
      } else if (valor instanceof List<?> filas) {
        Map<String, Object> item = mapa(propiedad.get("items"));
        for (int i = 0; i < filas.size(); i++) {
          if (filas.get(i) instanceof Map<?, ?> fila) {
            faltantes(item, campo + "[" + i + "].", mapa(fila), errores);
          }
        }
      }
    }
  }

  private static boolean esObjeto(Map<String, Object> propiedad) {
    return "object".equals(propiedad.get("type"));
  }

  private static List<String> requeridos(Map<String, Object> nodo) {
    return nodo.get("required") instanceof List<?> nombres
        ? nombres.stream().map(String.class::cast).toList()
        : List.of();
  }

  private static void lanzarSiHay(List<ErrorCampo> errores, String detalle) {
    if (errores.isEmpty()) {
      return;
    }
    throw new NegocioException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "datos-invalidos",
            "Los datos no cumplen el esquema",
            detalle)
        .conErrores(errores.stream().distinct().toList());
  }

  /** Un valor vacío (null) cuenta como ausente, igual que en el motor del frontend. */
  private static Map<String, Object> sinNulos(Map<String, Object> datos) {
    return mapa(limpiar(Objects.requireNonNull(datos)));
  }

  private static Object limpiar(Object valor) {
    if (valor instanceof Map<?, ?> mapa) {
      Map<String, Object> limpio = new LinkedHashMap<>();
      mapa.forEach(
          (clave, hijo) -> {
            if (hijo != null) {
              limpio.put(String.valueOf(clave), limpiar(hijo));
            }
          });
      return limpio;
    }
    if (valor instanceof List<?> filas) {
      return filas.stream().map(ConsultaFormularioEsquema::limpiar).toList();
    }
    return valor;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapa(Object valor) {
    return valor instanceof Map<?, ?> mapa ? (Map<String, Object>) mapa : Map.of();
  }
}
