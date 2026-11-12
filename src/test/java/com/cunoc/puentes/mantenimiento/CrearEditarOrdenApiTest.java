package com.cunoc.puentes.mantenimiento;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.usuario.Rol;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** B9: alta manual, corrección de orden abierta y bloqueo de órdenes cerradas. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql({"/mantenimiento/ordenes.sql"})
class CrearEditarOrdenApiTest extends PruebaIntegracion {

  private static final UUID PUENTE = UUID.fromString("019a0022-0000-7000-8000-000000000001");
  private static final UUID CANCELADA = UUID.fromString("019a0022-0000-7000-8000-000000000010");
  private static final UUID CATEDRATICO = UUID.fromString("019a0022-0000-7000-8000-0000000000c1");
  private static final String BASE = "/api/v1/puentes/" + PUENTE + "/ordenes-mantenimiento";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  // La auditoría exige un usuario real: el catedrático de la prueba se inserta dentro de la
  // transacción de cada caso, que se revierte al terminar
  @BeforeEach
  void catedratico() {
    jdbc.update(
        "INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en)"
            + " VALUES (?, 'catedratico@mantenimiento-prueba.com', 'Catedrático', 'hash-no-utilizable',"
            + " 'CATEDRATICO', 'ACTIVO', now())",
        CATEDRATICO);
  }

  @Test
  void elCatedraticoDaDeAltaUnaPropuestaManualConElTextoRecortado() throws Exception {
    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tipo": "CORRECTIVO", "descripcion": "  Grieta en el estribo  ",
                     "elemento_ref": "subestructura.estribo", "prioridad": "ALTA"}
                    """)
                .with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estado").value("PROPUESTA"))
        .andExpect(jsonPath("$.descripcion").value("Grieta en el estribo"));
  }

  @Test
  void laDescripcionDeUnaOrdenAbiertaSePuedeCorregirPeroNoQuedaVacia() throws Exception {
    String id =
        mvc.perform(
                post(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"tipo\": \"PREVENTIVO\", \"descripcion\": \"Limpieza\", \"prioridad\": \"BAJA\"}")
                    .with(como(Rol.CATEDRATICO, CATEDRATICO)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String ordenId = JsonPath.read(id, "$.id");

    mvc.perform(
            patch(BASE + "/" + ordenId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\": \"CORRECTIVO\", \"descripcion\": \"Limpieza y sellado\"}")
                .with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tipo").value("CORRECTIVO"))
        .andExpect(jsonPath("$.descripcion").value("Limpieza y sellado"));

    mvc.perform(
            patch(BASE + "/" + ordenId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\": \"   \"}")
                .with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().is(422))
        .andExpect(
            jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/descripcion-requerida")));
  }

  @Test
  void unaOrdenCerradaNoSeEdita() throws Exception {
    mvc.perform(
            patch(BASE + "/" + CANCELADA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\": \"Intento de cambio\"}")
                .with(como(Rol.CATEDRATICO, CATEDRATICO)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/orden-cerrada")));
  }

  @Test
  void soloCatedraticoOAdministradorCreaYEdita() throws Exception {
    mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"tipo\": \"PREVENTIVO\", \"descripcion\": \"x\", \"prioridad\": \"BAJA\"}")
                .with(como(Rol.ESTUDIANTE)))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch(BASE + "/" + CANCELADA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descripcion\": \"x\"}")
                .with(como(Rol.PROFESIONAL_EXTERNO)))
        .andExpect(status().isForbidden());
  }
}
