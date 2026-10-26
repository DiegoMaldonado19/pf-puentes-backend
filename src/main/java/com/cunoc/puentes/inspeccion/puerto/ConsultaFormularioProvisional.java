package com.cunoc.puentes.inspeccion.puerto;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.formulario.EstadoFormulario;
import com.cunoc.puentes.formulario.FormularioVersion;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Sustituto de P4 mientras el motor de formularios no esté en {@code develop}. Lee la versión
 * {@code ACTIVA} de {@code formulario_version} y las rutas de {@code sgp.formulario.*}. {@code
 * validarParcial} comprueba tipos y rangos; {@code validarCompleto} exige todas las rutas conocidas
 * (permanentes y daño) de esa versión.
 */
class ConsultaFormularioProvisional implements ConsultaFormulario {

  private final EntityManager em;
  private final Set<String> permanentes;
  private final Set<String> danos;

  ConsultaFormularioProvisional(
      EntityManager em, List<String> rutasPermanentes, List<String> rutasDano) {
    this.em = em;
    this.permanentes = copiar(rutasPermanentes);
    this.danos = copiar(rutasDano);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<VersionFormularioVista> versionActiva() {
    List<FormularioVersion> activas =
        em.createQuery(
                "select f from FormularioVersion f where f.estado = :estado",
                FormularioVersion.class)
            .setParameter("estado", EstadoFormulario.ACTIVA)
            .setMaxResults(1)
            .getResultList();
    return activas.stream().findFirst().map(this::vista);
  }

  @Override
  @Transactional(readOnly = true)
  public VersionFormularioVista exigir(UUID id) {
    Objects.requireNonNull(id, "El id de la versión es obligatorio.");
    FormularioVersion version = em.find(FormularioVersion.class, id);
    if (version == null) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "formulario-no-encontrado",
          "Versión de formulario no encontrada",
          "No hay una versión de formulario con ese id.");
    }
    return vista(version);
  }

  @Override
  public Set<String> rutasPermanentes(UUID versionId) {
    Objects.requireNonNull(versionId, "La versión del formulario es obligatoria.");
    return permanentes;
  }

  @Override
  public Set<String> rutasDeDano(UUID versionId) {
    Objects.requireNonNull(versionId, "La versión del formulario es obligatoria.");
    return danos;
  }

  @Override
  public List<GrupoElemento> grupos(UUID versionId) {
    Objects.requireNonNull(versionId, "La versión del formulario es obligatoria.");
    Map<String, Set<String>> porGrupo = new LinkedHashMap<>();
    for (String ruta : danos) {
      int punto = ruta.lastIndexOf('.');
      String grupo = punto < 0 ? ruta : ruta.substring(0, punto);
      porGrupo.computeIfAbsent(grupo, ignorado -> new LinkedHashSet<>()).add(ruta);
    }
    return porGrupo.entrySet().stream()
        .map(entrada -> new GrupoElemento(entrada.getKey(), entrada.getValue()))
        .toList();
  }

  @Override
  public void validarParcial(Map<String, Object> datos, UUID versionId) {
    Objects.requireNonNull(datos, "Los datos del formulario son obligatorios.");
    Objects.requireNonNull(versionId, "La versión del formulario es obligatoria.");
    List<ErrorCampo> errores = new ArrayList<>();
    for (String ruta : permanentes) {
      validarRuta(datos, ruta, false, false, errores);
    }
    for (String ruta : danos) {
      validarRuta(datos, ruta, true, false, errores);
    }
    lanzarSiHay(errores, "Hay valores con tipo o rango inválido.");
  }

  @Override
  public void validarCompleto(Map<String, Object> datos, UUID versionId) {
    Objects.requireNonNull(datos, "Los datos del formulario son obligatorios.");
    Objects.requireNonNull(versionId, "La versión del formulario es obligatoria.");
    List<ErrorCampo> errores = new ArrayList<>();
    for (String ruta : permanentes) {
      validarRuta(datos, ruta, false, true, errores);
    }
    for (String ruta : danos) {
      validarRuta(datos, ruta, true, true, errores);
    }
    lanzarSiHay(errores, "Faltan campos obligatorios o hay valores inválidos.");
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
        .conErrores(List.copyOf(errores));
  }

  private static void validarRuta(
      Map<String, Object> datos,
      String ruta,
      boolean dano,
      boolean obligatorio,
      List<ErrorCampo> errores) {
    Object valor = RutasDatos.leer(datos, ruta);
    if (valor == null) {
      if (obligatorio) {
        errores.add(new ErrorCampo(ruta, "Requerido"));
      }
      return;
    }
    String cola = cola(ruta);
    if (dano || "calificacion".equals(cola)) {
      if (!enteroEnRango(valor, 0, 4)) {
        errores.add(
            new ErrorCampo(ruta, "La calificación de daño debe ser un entero entre 0 y 4."));
      }
      return;
    }
    if ("numero_tramos".equals(cola) || "anio_construccion".equals(cola)) {
      if (!enteroEnRango(valor, 1, 3000)) {
        errores.add(new ErrorCampo(ruta, "Debe ser un número entero."));
      }
      return;
    }
    if (cola.endsWith("_m")) {
      if (!numeroPositivo(valor)) {
        errores.add(new ErrorCampo(ruta, "Debe ser un número positivo."));
      }
      return;
    }
    if (!(valor instanceof String) && !(valor instanceof Number) && !(valor instanceof Boolean)) {
      errores.add(new ErrorCampo(ruta, "El valor no coincide con el tipo esperado."));
    }
  }

  private static String cola(String ruta) {
    int punto = ruta.lastIndexOf('.');
    return punto < 0 ? ruta : ruta.substring(punto + 1);
  }

  private static boolean enteroEnRango(Object valor, int minimo, int maximo) {
    if (!(valor instanceof Number numero)) {
      return false;
    }
    double crudo = numero.doubleValue();
    if (crudo != Math.rint(crudo)) {
      return false;
    }
    long entero = numero.longValue();
    return entero >= minimo && entero <= maximo;
  }

  private static boolean numeroPositivo(Object valor) {
    return valor instanceof Number numero && numero.doubleValue() > 0;
  }

  private VersionFormularioVista vista(FormularioVersion version) {
    return new VersionFormularioVista(version.getId(), version.getCodigo(), version.getEsquema());
  }

  private static Set<String> copiar(List<String> rutas) {
    if (rutas == null) {
      return Set.of();
    }
    return rutas.stream()
        .filter(Objects::nonNull)
        .map(String::strip)
        .filter(ruta -> !ruta.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
