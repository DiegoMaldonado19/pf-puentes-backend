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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Formulario de pruebas con rutas fijas, anterior al esquema real de P4. Las pruebas de P5 que usan
 * un esquema {@code {"type":"object"}} dependen de él; {@code sgp.pruebas.formulario-real=true} lo
 * desactiva.
 */
@Component
@Primary
@ConditionalOnProperty(
    name = "sgp.pruebas.formulario-real",
    havingValue = "false",
    matchIfMissing = true)
class ConsultaFormularioProvisional implements ConsultaFormulario {

  private static final List<String> RUTAS_PERMANENTES =
      List.of(
          "identificacion.tipologia",
          "identificacion.material_principal",
          "identificacion.numero_tramos",
          "identificacion.longitud_m",
          "identificacion.ancho_calzada_m",
          "identificacion.anio_construccion");
  private static final List<String> RUTAS_DANO =
      List.of(
          "subestructura.estribo_entrada.calificacion",
          "subestructura.estribo_entrada.fisuras",
          "subestructura.estribo_entrada.humedad",
          "subestructura.estribo_entrada.desconchado",
          "subestructura.estribo_entrada.asentamiento",
          "subestructura.estribo_entrada.socavacion",
          "subestructura.estribo_salida.calificacion",
          "superestructura.losa.calificacion",
          "accesos.entrada.calificacion",
          "cauce.calificacion");

  private final EntityManager em;
  private final Set<String> permanentes = new LinkedHashSet<>(RUTAS_PERMANENTES);
  private final Set<String> danos = new LinkedHashSet<>(RUTAS_DANO);

  ConsultaFormularioProvisional(EntityManager em) {
    this.em = em;
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

  @Override
  public Map<String, Object> traducir(Map<String, Object> datos, UUID origen, UUID destino) {
    return datos;
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
}
