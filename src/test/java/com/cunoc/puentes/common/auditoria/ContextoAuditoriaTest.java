package com.cunoc.puentes.common.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ContextoAuditoriaTest {

  private static final UUID USUARIO = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");

  private final ContextoAuditoria contexto = new ContextoAuditoria();

  @AfterEach
  void limpiar() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void sinSesionActuaElSistema() {
    assertThat(contexto.usuario()).isEmpty();
  }

  @Test
  void unaSesionAnonimaTambienEsElSistema() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "clave", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertThat(contexto.usuario()).isEmpty();
  }

  @Test
  void conSesionDevuelveElIdDelUsuario() {
    autenticarComo(USUARIO.toString());

    assertThat(contexto.usuario()).contains(USUARIO);
  }

  @Test
  void unNombreQueNoEsUuidFallaEnLugarDeAtribuirMalLaAccion() {
    autenticarComo("admin");

    assertThatThrownBy(contexto::usuario).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fueraDeHttpNoHayOrigenIp() {
    assertThat(contexto.origenIp()).isEmpty();
  }

  @Test
  void dentroDeHttpElOrigenEsLaIpDeLaPeticion() {
    MockHttpServletRequest peticion = new MockHttpServletRequest();
    peticion.setRemoteAddr("203.0.113.7");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));

    assertThat(contexto.origenIp()).contains("203.0.113.7");
  }

  @Test
  void unaPeticionTieneUnaSolaCorrelacionYOtraPeticionTieneOtra() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    UUID primera = contexto.correlacion();
    assertThat(contexto.correlacion()).isEqualTo(primera);

    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));

    assertThat(contexto.correlacion()).isNotEqualTo(primera);
  }

  @Test
  void fueraDeHttpLaLlamadaMasExternaDefineLaCorrelacion() {
    UUID externa;
    try (var _ = contexto.abrirCorrelacion()) {
      externa = contexto.correlacion();
      try (var _ = contexto.abrirCorrelacion()) {
        assertThat(contexto.correlacion()).isEqualTo(externa);
      }
      assertThat(contexto.correlacion()).isEqualTo(externa);
    }

    assertThat(contexto.correlacion()).isNull();
    try (var _ = contexto.abrirCorrelacion()) {
      assertThat(contexto.correlacion()).isNotEqualTo(externa);
    }
  }

  private static void autenticarComo(String nombre) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(nombre, null, List.of()));
  }
}
