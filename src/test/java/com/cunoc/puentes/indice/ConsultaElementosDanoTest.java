package com.cunoc.puentes.indice;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql("/indice/servicio-pesos.sql")
class ConsultaElementosDanoTest extends PruebaIntegracion {
  private static final UUID SIECA = UUID.fromString("0192b000-0000-7000-8000-000000000001");
  private static final UUID VACIO = UUID.fromString("019a0018-0000-7000-8000-000000000001");
  private static final String RUTA = "/api/v1/formularios/{id}/elementos-dano";
  @Autowired MockMvc mvc;
  @Autowired ConsultaElementosDanoService servicio;
  @Autowired MetadatosDanoFormulario metadatos;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void limpiarSesion() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void paginaLasReferenciasDeLaSemillaRealSinNecesitarPesosNiEscribir() throws Exception {
    var referencias = metadatos.obtener(SIECA).referencias().stream().sorted().toList();
    Long auditorias = jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class);
    Long juegos = jdbc.queryForObject("SELECT count(*) FROM version_pesos", Long.class);
    mvc.perform(get(RUTA, SIECA).param("size", "1").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0]").value(referencias.getFirst()))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.total_elements").value(19))
        .andExpect(jsonPath("$.total_pages").value(19));
    mvc.perform(
            get(RUTA, SIECA).param("size", "1").param("page", "1").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0]").value(referencias.get(1)));
    mvc.perform(get(RUTA, SIECA).param("page", "100").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.total_elements").value(19));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class))
        .isEqualTo(auditorias);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM version_pesos", Long.class))
        .isEqualTo(juegos);
  }

  @Test
  void distingueVacioVersionAusenteYMetadatosIncompatibles() throws Exception {
    mvc.perform(get(RUTA, VACIO).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.size").value(20));
    mvc.perform(
            get(RUTA, UUID.fromString("019a0025-0000-7000-8000-000000000099"))
                .with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isNotFound());
    jdbc.update("UPDATE formulario_version SET esquema='[]'::jsonb WHERE id=?", VACIO);
    mvc.perform(get(RUTA, VACIO).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isUnprocessableContent());
  }

  @ParameterizedTest
  @CsvSource({"-1,20", "0,0", "0,101", "2147483647,100"})
  void rechazaPaginacionTambienEnElServicio(int pagina, int tamano) throws Exception {
    mvc.perform(
            get(RUTA, SIECA)
                .param("page", "" + pagina)
                .param("size", "" + tamano)
                .with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isBadRequest());
    sesion(Rol.ADMINISTRADOR);
    assertThatThrownBy(() -> servicio.listar(SIECA, pagina, tamano))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @EnumSource(value = Rol.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
  void soloAdministradorEnHttpYServicio(Rol rol) throws Exception {
    mvc.perform(get(RUTA, SIECA).with(como(rol))).andExpect(status().isForbidden());
    sesion(rol);
    assertThatThrownBy(() -> servicio.listar(SIECA, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void requiereSesionYUuidValido() throws Exception {
    mvc.perform(get(RUTA, SIECA)).andExpect(status().isUnauthorized());
    assertThatThrownBy(() -> servicio.listar(SIECA, 0, 20))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    mvc.perform(get(RUTA, "incorrecto").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isBadRequest());
  }

  private void sesion(Rol rol) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "prueba", "N/A", List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
  }
}
