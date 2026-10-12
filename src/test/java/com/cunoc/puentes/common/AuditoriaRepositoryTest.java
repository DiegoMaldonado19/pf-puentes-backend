package com.cunoc.puentes.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class AuditoriaRepositoryTest extends PruebaIntegracion {

  @Autowired AuditoriaRepository repositorio;

  @Autowired TestEntityManager em;

  @Test
  void persisteYLeeUnRegistroCompletoConSusValoresJson() {
    UUID entidadId = UUID.randomUUID();
    UUID correlacion = UUID.randomUUID();
    Auditoria auditoria = registro("CAMBIAR_ESTADO", "usuario");
    auditoria.setEntidadId(entidadId);
    auditoria.setValorAnterior(Map.of("estado", "ACTIVO"));
    auditoria.setValorNuevo(Map.of("estado", "INACTIVO"));
    auditoria.setMotivo("Ya no pertenece al curso");
    auditoria.setOrigenIp("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
    auditoria.setCorrelacionId(correlacion);
    UUID id = repositorio.saveAndFlush(auditoria).getId();
    em.clear();

    Auditoria leida = repositorio.findById(id).orElseThrow();

    assertThat(leida.getAccion()).isEqualTo("CAMBIAR_ESTADO");
    assertThat(leida.getEntidad()).isEqualTo("usuario");
    assertThat(leida.getEntidadId()).isEqualTo(entidadId);
    assertThat(leida.getValorAnterior()).containsExactly(Map.entry("estado", "ACTIVO"));
    assertThat(leida.getValorNuevo()).containsExactly(Map.entry("estado", "INACTIVO"));
    assertThat(leida.getMotivo()).isEqualTo("Ya no pertenece al curso");
    assertThat(leida.getOrigenIp()).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
    assertThat(leida.getCorrelacionId()).isEqualTo(correlacion);
    assertThat(leida.getCreadoEn()).isNotNull();
  }

  @Test
  void lasColumnasNuevasSonOpcionalesParaLasAccionesDelSistema() {
    UUID id = repositorio.saveAndFlush(registro("CREAR", "puente")).getId();
    em.clear();

    Auditoria leida = repositorio.findById(id).orElseThrow();

    assertThat(leida.getUsuarioId()).isNull();
    assertThat(leida.getMotivo()).isNull();
    assertThat(leida.getOrigenIp()).isNull();
    assertThat(leida.getCorrelacionId()).isNull();
  }

  @Test
  void laBaseRechazaUnaAccionConFormatoIncorrecto() {
    assertThatThrownBy(() -> repositorio.saveAndFlush(registro("crear", "puente")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auditoria_accion_ck");
  }

  // Cada rechazo aborta la transacción de la prueba, por eso van en pruebas separadas
  @Test
  void laBaseRechazaUnaEntidadConFormatoIncorrecto() {
    assertThatThrownBy(() -> repositorio.saveAndFlush(registro("CREAR", "Puente")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auditoria_entidad_ck");
  }

  @Test
  void laBaseRechazaUnMotivoEnBlanco() {
    Auditoria auditoria = registro("MODERAR", "comentario");
    auditoria.setMotivo("   ");

    assertThatThrownBy(() -> repositorio.saveAndFlush(auditoria))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("auditoria_motivo_ck");
  }

  private static Auditoria registro(String accion, String entidad) {
    Auditoria auditoria = new Auditoria();
    auditoria.setAccion(accion);
    auditoria.setEntidad(entidad);
    return auditoria;
  }
}
