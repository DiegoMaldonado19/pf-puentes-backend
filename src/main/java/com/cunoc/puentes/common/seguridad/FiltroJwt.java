package com.cunoc.puentes.common.seguridad;

import com.cunoc.puentes.usuario.EstadoCuentaService;
import com.cunoc.puentes.usuario.Rol;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica con {@code Authorization: Bearer}. Nunca responde: si el token falta, es inválido o la
 * cuenta no está ACTIVA, deja la petición anónima y la cadena decide (401 en rutas privadas).
 *
 * <p>El nombre de la autenticación es el UUID del usuario (contrato 14) y el rol sale de la base,
 * no del token, así que una baja o un cambio de rol surten efecto de inmediato (RN-USR-08).
 */
final class FiltroJwt extends OncePerRequestFilter {

  private static final String PREFIJO = "Bearer ";

  private final TokenService tokens;
  private final EstadoCuentaService cuentas;

  FiltroJwt(TokenService tokens, EstadoCuentaService cuentas) {
    this.tokens = tokens;
    this.cuentas = cuentas;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
      throws ServletException, IOException {
    String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (cabecera != null && cabecera.startsWith(PREFIJO)) {
      tokens
          .validar(cabecera.substring(PREFIJO.length()).trim())
          .ifPresent(token -> autenticar(token, cuentas.rolSiEstaActiva(token.usuarioId())));
    }
    cadena.doFilter(request, response);
  }

  private static void autenticar(TokenAcceso token, Optional<Rol> rolVigente) {
    rolVigente.ifPresent(
        rol ->
            SecurityContextHolder.getContext()
                .setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                        token.usuarioId().toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())))));
  }
}
