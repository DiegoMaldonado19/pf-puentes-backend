package com.cunoc.puentes.inspeccion;

import static com.cunoc.puentes.common.seguridad.AutenticacionDePrueba.como;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.common.seguridad.SeguridadDePrueba;
import com.cunoc.puentes.usuario.Rol;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SeguridadDePrueba.class)
@Transactional
@Sql("/archivo/inspecciones.sql")
class InspeccionArchivosTest extends PruebaIntegracion {

  private static final UUID AUTOR = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");
  private static final UUID BORRADOR = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b1");
  private static final UUID PUBLICADA = UUID.fromString("0192f5a0-0000-7000-8000-0000000000b3");

  @Autowired MockMvc mvc;

  @Test
  void elAutorVeLosArchivosDeSuBorrador() throws Exception {
    mvc.perform(
            get("/api/v1/inspecciones/{id}/archivos", BORRADOR).with(como(Rol.ESTUDIANTE, AUTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].url").isNotEmpty());
  }

  @Test
  void otroEstudianteNoVeElBorrador() throws Exception {
    mvc.perform(
            get("/api/v1/inspecciones/{id}/archivos", BORRADOR)
                .with(como(Rol.ESTUDIANTE, UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void elAdministradorSoloVeLasPublicadas() throws Exception {
    mvc.perform(
            get("/api/v1/inspecciones/{id}/archivos", PUBLICADA)
                .with(como(Rol.ADMINISTRADOR, UUID.randomUUID())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(
            get("/api/v1/inspecciones/{id}/archivos", BORRADOR)
                .with(como(Rol.ADMINISTRADOR, UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void sinSesionNoHayAcceso() throws Exception {
    mvc.perform(get("/api/v1/inspecciones/{id}/archivos", BORRADOR))
        .andExpect(status().isUnauthorized());
  }
}
