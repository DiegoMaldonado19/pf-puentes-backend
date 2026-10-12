package com.cunoc.puentes.common.seguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Contrato 12: autentica una petición de {@code MockMvc} como un usuario con un rol, igual que la
 * deja el filtro JWT en producción (el nombre es el UUID del usuario y la autoridad es {@code
 * ROLE_<rol>}).
 *
 * <pre>{@code
 * mvc.perform(get("/api/v1/puentes").with(como(Rol.CATEDRATICO, usuarioId)))
 * }</pre>
 *
 * Úsalo con {@link SeguridadDePrueba} para que {@code @PreAuthorize} y los 401/403 sean los reales.
 */
public final class AutenticacionDePrueba {

  /** Id que usa {@link #como(Rol)} cuando a la prueba no le importa quién es el usuario. */
  public static final UUID USUARIO_POR_DEFECTO =
      UUID.fromString("0192f5a0-0000-7000-8000-000000000002");

  private AutenticacionDePrueba() {}

  public static RequestPostProcessor como(Rol rol, UUID usuarioId) {
    return user(usuarioId.toString()).roles(rol.name());
  }

  public static RequestPostProcessor como(Rol rol) {
    return como(rol, USUARIO_POR_DEFECTO);
  }
}
