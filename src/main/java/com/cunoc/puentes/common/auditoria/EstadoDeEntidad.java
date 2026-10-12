package com.cunoc.puentes.common.auditoria;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * Foto de una fila como mapa {@code columna -> valor}, ya saneada (DT-SEC-12). Las asociaciones se
 * reducen al id del otro lado, para no cargar relaciones perezosas ni serializar ciclos.
 */
@Component
class EstadoDeEntidad {

  private final EntityManager em;
  private final Map<String, EntityType<?>> tipos = new ConcurrentHashMap<>();

  EstadoDeEntidad(EntityManager em) {
    this.em = em;
  }

  boolean existe(String entidad) {
    return tipo(entidad).isPresent();
  }

  /** Vacío si la fila no existe (todavía, o ya no). */
  Optional<Map<String, Object>> leer(String entidad, UUID id) {
    EntityType<?> tipo =
        tipo(entidad)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "La entidad auditable '%s' no existe".formatted(entidad)));
    return Optional.ofNullable(em.find(tipo.getJavaType(), id))
        .map(fila -> SaneadorDeAuditoria.sanear(valores(tipo, Hibernate.unproxy(fila))));
  }

  /** Envía a la base los cambios pendientes: así la foto posterior es la que quedó guardada. */
  void sincronizar() {
    em.flush();
  }

  private Map<String, Object> valores(EntityType<?> tipo, Object fila) {
    Map<String, Object> valores = new LinkedHashMap<>();
    for (SingularAttribute<?, ?> atributo : tipo.getSingularAttributes()) {
      Object valor = leerAtributo(atributo, fila);
      valores.put(aSnakeCase(atributo.getName()), atributo.isAssociation() ? idDe(valor) : valor);
    }
    return valores;
  }

  private Object idDe(Object asociado) {
    return asociado == null
        ? null
        : em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(asociado);
  }

  private static Object leerAtributo(SingularAttribute<?, ?> atributo, Object fila) {
    Member miembro = atributo.getJavaMember();
    if (miembro instanceof Field campo) {
      ReflectionUtils.makeAccessible(campo);
      return ReflectionUtils.getField(campo, fila);
    }
    throw new IllegalStateException(
        "No se puede leer el atributo '%s' para la auditoría".formatted(atributo.getName()));
  }

  private Optional<EntityType<?>> tipo(String entidad) {
    if (tipos.isEmpty()) {
      em.getMetamodel()
          .getEntities()
          .forEach(tipo -> tipos.put(aSnakeCase(tipo.getJavaType().getSimpleName()), tipo));
    }
    return Optional.ofNullable(tipos.get(entidad));
  }

  static String aSnakeCase(String nombre) {
    return nombre.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
  }
}
