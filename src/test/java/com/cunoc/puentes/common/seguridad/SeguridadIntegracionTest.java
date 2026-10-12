package com.cunoc.puentes.common.seguridad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La aplicación completa con el perfil de producción: {@code /api/docs} exige sesión (DT-BE-12) y
 * el estado de la cuenta se consulta en la base real en cada petición (RN-USR-08, RN-USR-10).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class SeguridadIntegracionTest extends PruebaIntegracion {

  private static final String DOCS = "/api/docs";

  @Autowired MockMvc mvc;
  @Autowired TokenService tokens;
  @Autowired JdbcTemplate jdbc;

  private UUID usuario;

  @AfterEach
  void limpiar() {
    if (usuario != null) {
      jdbc.update("DELETE FROM usuario WHERE id = ?", usuario);
    }
  }

  @Test
  void laDocumentacionSinSesionResponde401EnProduccion() throws Exception {
    mvc.perform(get(DOCS)).andExpect(status().isUnauthorized());
    mvc.perform(get(DOCS + "/swagger-ui.html")).andExpect(status().isUnauthorized());
  }

  @Test
  void unaCuentaActivaConTokenVigenteVeLaDocumentacion() throws Exception {
    crearUsuario("ACTIVO", Rol.ESTUDIANTE);

    mvc.perform(get(DOCS).header(HttpHeaders.AUTHORIZATION, bearer(Rol.ESTUDIANTE)))
        .andExpect(status().isOk());
  }

  @Test
  void unaCuentaQuePasaAInactivaPierdeElAccesoSinEsperarQueExpireElToken() throws Exception {
    crearUsuario("ACTIVO", Rol.ESTUDIANTE);
    String autorizacion = bearer(Rol.ESTUDIANTE);
    mvc.perform(get(DOCS).header(HttpHeaders.AUTHORIZATION, autorizacion))
        .andExpect(status().isOk());

    jdbc.update(
        "UPDATE usuario SET estado = 'INACTIVO', desactivado_en = now(), motivo_baja = 'prueba'"
            + " WHERE id = ?",
        usuario);

    mvc.perform(get(DOCS).header(HttpHeaders.AUTHORIZATION, autorizacion))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void unaCuentaPendienteNoAccede() throws Exception {
    crearUsuario("PENDIENTE", Rol.ESTUDIANTE);

    mvc.perform(get(DOCS).header(HttpHeaders.AUTHORIZATION, bearer(Rol.ESTUDIANTE)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unTokenDeUnaCuentaInexistenteNoAccede() throws Exception {
    String token = tokens.emitirAcceso(UUID.randomUUID(), Rol.ADMINISTRADOR);

    mvc.perform(get(DOCS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  private String bearer(Rol rol) {
    return "Bearer " + tokens.emitirAcceso(usuario, rol);
  }

  private void crearUsuario(String estado, Rol rol) {
    usuario = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en)"
            + " VALUES (?, ?, 'Prueba', 'hash', ?, ?, CASE WHEN ? = 'PENDIENTE' THEN NULL ELSE now() END)",
        usuario,
        usuario + "@correo.com",
        rol.name(),
        estado,
        estado);
  }
}
