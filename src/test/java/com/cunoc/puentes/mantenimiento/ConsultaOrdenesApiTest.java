package com.cunoc.puentes.mantenimiento;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/mantenimiento/ordenes.sql", "/mantenimiento/propuestas.sql"})
class ConsultaOrdenesApiTest extends PruebaIntegracion {
  private static final UUID PUENTE = UUID.fromString("019a0022-0000-7000-8000-000000000001");
  private static final UUID OTRO = UUID.fromString("019a0022-0000-7000-8000-000000000002");
  private static final UUID CANCELADA = UUID.fromString("019a0022-0000-7000-8000-000000000010");
  private static final String RUTA = "/api/v1/puentes/" + PUENTE + "/ordenes-mantenimiento";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ConsultaOrdenesService servicio;

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ADMINISTRADOR", "CATEDRATICO"})
  void losLectoresConfirmadosConsultanListaYDetalleTerminal(Rol rol) throws Exception {
    mvc.perform(get(RUTA).with(como(rol)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.puente_id").value(PUENTE.toString()))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.total_elements").value(7))
        .andExpect(jsonPath("$.total_pages").value(1))
        .andExpect(
            jsonPath(
                "$.content[*].estado",
                hasItems(
                    "PROPUESTA",
                    "PROGRAMADA",
                    "EN_EJECUCION",
                    "EJECUTADA",
                    "DESCARTADA",
                    "CANCELADA")))
        .andExpect(jsonPath("$.content[0].id").value("019a0026-0000-7000-8000-000000000017"))
        .andExpect(jsonPath("$.totalElements").doesNotExist());
    mvc.perform(get(RUTA + "/" + CANCELADA).with(como(rol)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CANCELADA"))
        .andExpect(jsonPath("$.puente_id").value(PUENTE.toString()))
        .andExpect(jsonPath("$.motivo").value("Motivo histórico de prueba"))
        .andExpect(jsonPath("$.inspeccion_id").isEmpty())
        .andExpect(jsonPath("$.creado_en").isNotEmpty())
        .andExpect(jsonPath("$.puenteId").doesNotExist());
  }

  @Test
  void paginaEnElServidorConOrdenEstableYTotalesDelPuente() throws Exception {
    mvc.perform(get(RUTA).param("size", "1").param("page", "1").with(como(Rol.CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(7))
        .andExpect(jsonPath("$.total_pages").value(7))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value("019a0026-0000-7000-8000-000000000016"));
    mvc.perform(get(RUTA.replace(PUENTE.toString(), OTRO.toString())).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.content[0].puente_id").value(OTRO.toString()));
    mvc.perform(get(RUTA).param("page", "10").param("size", "100").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.total_elements").value(7));
    jdbc.update("DELETE FROM orden_mantenimiento WHERE puente_id = ?", OTRO);
    mvc.perform(get(RUTA.replace(PUENTE.toString(), OTRO.toString())).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty())
        .andExpect(jsonPath("$.puente_id").value(OTRO.toString()))
        .andExpect(jsonPath("$.total_elements").value(0));
  }

  @ParameterizedTest
  @EnumSource(
      value = Rol.class,
      names = {"ESTUDIANTE", "PROFESIONAL_EXTERNO"})
  void prohibeOtrosRolesEnAmbasRutasYDirectamenteEnServicio(Rol rol) throws Exception {
    mvc.perform(get(RUTA).with(como(rol))).andExpect(status().isForbidden());
    mvc.perform(get(RUTA + "/" + CANCELADA).with(como(rol))).andExpect(status().isForbidden());
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "prueba", null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
    try {
      assertThatThrownBy(() -> servicio.listar(PUENTE, 0, 20))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> servicio.obtener(PUENTE, CANCELADA))
          .isInstanceOf(AccessDeniedException.class);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void requiereSesionEnAmbasRutas() throws Exception {
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    mvc.perform(get(RUTA + "/" + CANCELADA)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"page=-1", "size=0", "size=101", "size=no", "page=2147483647&size=100"})
  void rechazaPaginacionInvalida(String parametros) throws Exception {
    mvc.perform(get(RUTA + "?" + parametros).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  void noExponeOrdenesDeOtroPuenteYDevuelve404SinDistinguirAusencia() throws Exception {
    String ajeno = RUTA.replace(PUENTE.toString(), OTRO.toString()) + "/" + CANCELADA;
    mvc.perform(get(ajeno).with(como(Rol.CATEDRATICO))).andExpect(status().isNotFound());
    mvc.perform(get(RUTA + "/" + UUID.randomUUID()).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isNotFound());
    mvc.perform(
            get(RUTA.replace(PUENTE.toString(), UUID.randomUUID().toString()))
                .with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isNotFound());
  }

  @Test
  void validaAmbosUuid() throws Exception {
    mvc.perform(get(RUTA + "/invalido").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isBadRequest());
    mvc.perform(get(RUTA.replace(PUENTE.toString(), "invalido")).with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void detalleConservaDatosDeEjecucionYSinEscrituras() throws Exception {
    Long auditorias = jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class);
    Long ordenes = jdbc.queryForObject("SELECT count(*) FROM orden_mantenimiento", Long.class);
    mvc.perform(get(RUTA + "/019a0026-0000-7000-8000-000000000016").with(como(Rol.ADMINISTRADOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EJECUTADA"))
        .andExpect(jsonPath("$.fecha_ejecucion").value("2026-10-19"))
        .andExpect(jsonPath("$.responsable").value("Responsable de prueba"));
    mvc.perform(get(RUTA).with(como(Rol.ADMINISTRADOR))).andExpect(status().isOk());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class))
        .isEqualTo(auditorias);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orden_mantenimiento", Long.class))
        .isEqualTo(ordenes);
  }
}
