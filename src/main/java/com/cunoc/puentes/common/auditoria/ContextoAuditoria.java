package com.cunoc.puentes.common.auditoria;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Quién, desde dónde y dentro de qué operación se ejecuta una acción auditable. */
@Component
class ContextoAuditoria {

  /** Cierra el alcance de una correlación abierta fuera de una petición HTTP. */
  interface Alcance extends AutoCloseable {
    @Override
    void close();
  }

  private static final String ATRIBUTO_CORRELACION =
      ContextoAuditoria.class.getName() + ".correlacion";
  private static final Alcance SIN_ALCANCE = () -> {};

  private final ThreadLocal<UUID> correlacionSinPeticion = new ThreadLocal<>();

  /** El id del usuario autenticado; vacío si no hay sesión, es decir, si actúa el Sistema. */
  Optional<UUID> usuario() {
    Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
    boolean sinSesion =
        autenticacion == null
            || autenticacion instanceof AnonymousAuthenticationToken
            || !autenticacion.isAuthenticated();
    // Contrato 14: el nombre es el UUID; si no lo es, falla en lugar de atribuir mal la acción
    return sinSesion ? Optional.empty() : Optional.of(UUID.fromString(autenticacion.getName()));
  }

  /** La IP que dejó el proxy ({@code server.forward-headers-strategy}); vacío fuera de HTTP. */
  Optional<String> origenIp() {
    return peticion().map(HttpServletRequest::getRemoteAddr);
  }

  /** Un solo id por petición HTTP, o por operación del Sistema si no hay petición. */
  UUID correlacion() {
    Optional<ServletRequestAttributes> peticion = atributos();
    if (peticion.isEmpty()) {
      return correlacionSinPeticion.get();
    }
    ServletRequestAttributes atributos = peticion.get();
    Object existente =
        atributos.getAttribute(ATRIBUTO_CORRELACION, RequestAttributes.SCOPE_REQUEST);
    if (existente instanceof UUID id) {
      return id;
    }
    UUID nueva = UUID.randomUUID();
    atributos.setAttribute(ATRIBUTO_CORRELACION, nueva, RequestAttributes.SCOPE_REQUEST);
    return nueva;
  }

  /** Fuera de una petición, la llamada auditable más externa define la correlación de las demás. */
  Alcance abrirCorrelacion() {
    if (atributos().isPresent() || correlacionSinPeticion.get() != null) {
      return SIN_ALCANCE;
    }
    correlacionSinPeticion.set(UUID.randomUUID());
    return correlacionSinPeticion::remove;
  }

  private Optional<HttpServletRequest> peticion() {
    return atributos().map(ServletRequestAttributes::getRequest);
  }

  private Optional<ServletRequestAttributes> atributos() {
    return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet
        ? Optional.of(servlet)
        : Optional.empty();
  }
}
