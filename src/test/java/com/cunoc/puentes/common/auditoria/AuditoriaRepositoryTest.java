package com.cunoc.puentes.common.auditoria;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest
class AuditoriaRepositoryTest extends PruebaIntegracion {

  @Autowired AuditoriaRepository repositorio;

  @Autowired TestEntityManager em;

  @Test
  void bitacoraAppendOnlyNoActualizaJsonNumericoConRolRestringido() {
    em.getEntityManager()
        .createNativeQuery(
            "do $$ begin if not exists (select from pg_roles where rolname = 'puentes_app') then create role puentes_app; end if; end $$")
        .executeUpdate();
    em.getEntityManager()
        .createNativeQuery("grant usage on schema public to puentes_app")
        .executeUpdate();
    em.getEntityManager()
        .createNativeQuery("grant select, insert on auditoria to puentes_app")
        .executeUpdate();
    em.getEntityManager()
        .createNativeQuery("revoke update, delete on auditoria from puentes_app")
        .executeUpdate();
    em.getEntityManager().createNativeQuery("set local role puentes_app").executeUpdate();
    Auditoria auditoria = registro("CREAR", "inspeccion");
    auditoria.setValorNuevo(new java.util.LinkedHashMap<>(Map.of("version", 1L)));
    UUID id = repositorio.saveAndFlush(auditoria).getId();
    // Fuerza el mismo dirty checking que puede disparar la normalización Long -> Integer del JSON.
    auditoria.getValorNuevo().put("version", 2);
    auditoria.setMotivo("No debe modificarse");
    em.flush();
    em.clear();
    Auditoria persistida = repositorio.findById(id).orElseThrow();
    assertThat(persistida.getValorNuevo().get("version")).isEqualTo(1);
    assertThat(persistida.getMotivo()).isNull();
    assertThat(
            em.getEntityManager()
                .createNativeQuery(
                    "select has_table_privilege(current_user, 'auditoria', 'UPDATE')")
                .getSingleResult())
        .isEqualTo(false);
  }

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

  @Test
  void filtraYPaginaLaBitacoraConEspecificaciones() {
    UUID entidadId = UUID.randomUUID();
    for (String accion : new String[] {"CREAR", "MODIFICAR", "CAMBIAR_ESTADO"}) {
      Auditoria auditoria = registro(accion, "usuario");
      auditoria.setEntidadId(entidadId);
      repositorio.save(auditoria);
    }
    Auditoria otra = registro("CREAR", "usuario");
    otra.setEntidadId(UUID.randomUUID());
    repositorio.saveAndFlush(otra);
    em.clear();
    Specification<Auditoria> deLaEntidad =
        (raiz, consulta, cb) -> cb.equal(raiz.get("entidadId"), entidadId);

    Page<Auditoria> pagina =
        repositorio.findAll(deLaEntidad, PageRequest.of(0, 2, Sort.by("accion")));

    assertThat(pagina.getTotalElements()).isEqualTo(3);
    assertThat(pagina.getContent())
        .extracting(Auditoria::getAccion)
        .containsExactly("CAMBIAR_ESTADO", "CREAR");
  }

  private static Auditoria registro(String accion, String entidad) {
    Auditoria auditoria = new Auditoria();
    auditoria.setAccion(accion);
    auditoria.setEntidad(entidad);
    return auditoria;
  }
}
