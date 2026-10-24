package com.cunoc.puentes.inspeccion.puerto;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.formulario.EstadoFormulario;
import com.cunoc.puentes.formulario.FormularioVersion;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sustituto de P4 mientras el motor de formularios no esté en {@code develop}. Lee la versión
 * {@code ACTIVA} de {@code formulario_version} y las rutas de {@code sgp.formulario.*}. No valida
 * JSON Schema: {@code validarParcial} y {@code validarCompleto} son no-op a propósito (fase 0).
 */
@Component
@ConditionalOnMissingBean(ConsultaFormulario.class)
class ConsultaFormularioProvisional implements ConsultaFormulario {

  private final EntityManager em;
  private final Set<String> permanentes;
  private final Set<String> danos;

  ConsultaFormularioProvisional(
      EntityManager em,
      @Value("${sgp.formulario.rutas-permanentes:}") List<String> rutasPermanentes,
      @Value("${sgp.formulario.rutas-dano:}") List<String> rutasDano) {
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
    return List.of();
  }

  @Override
  public void validarParcial(Map<String, Object> datos, UUID versionId) {
    Objects.requireNonNull(datos, "Los datos del formulario son obligatorios.");
    Objects.requireNonNull(versionId, "La versión del formulario es obligatoria.");
  }

  @Override
  public void validarCompleto(Map<String, Object> datos, UUID versionId) {
    Objects.requireNonNull(datos, "Los datos del formulario son obligatorios.");
    Objects.requireNonNull(versionId, "La versión del formulario es obligatoria.");
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
        .collect(Collectors.toUnmodifiableSet());
  }
}
