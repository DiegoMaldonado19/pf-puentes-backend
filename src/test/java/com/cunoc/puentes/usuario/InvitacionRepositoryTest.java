package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.usuario.Cuentas.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class InvitacionRepositoryTest extends PruebaIntegracion {

  @Autowired InvitacionRepository repositorio;

  @Autowired UsuarioRepository usuarios;

  @Autowired TestEntityManager em;

  @Test
  void consultaLaInvitacionAbiertaDeUnCorreoSinDistinguirMayusculas() {
    Usuario admin = usuarios.save(Cuentas.administrador("admin@correo.com"));
    Invitacion revocada = Cuentas.invitacion("prof@correo.com", admin);
    revocada.setRevocadaEn(AHORA);
    revocada.setRevocadaPor(admin);
    Invitacion abierta = Cuentas.invitacion("Prof@Correo.com", admin);
    abierta.setTokenHash(Cuentas.hash("abierta"));
    // la revocada va primero: el índice único solo admite una abierta por correo
    repositorio.saveAndFlush(revocada);
    repositorio.saveAndFlush(abierta);
    em.clear();

    assertThat(repositorio.buscarAbierta("PROF@correo.com"))
        .hasValueSatisfying(
            invitacion -> assertThat(invitacion.getId()).isEqualTo(abierta.getId()));
    assertThat(repositorio.buscarAbierta("otro@correo.com")).isEmpty();
  }

  @Test
  void unaInvitacionAceptadaOSoloRevocadaYaNoEstaAbierta() {
    Usuario admin = usuarios.save(Cuentas.administrador("admin@correo.com"));
    Usuario cuenta = usuarios.save(Cuentas.estudiante("acepto@correo.com"));
    Invitacion aceptada = Cuentas.invitacion("acepto@correo.com", admin);
    aceptada.setAceptadaEn(AHORA);
    aceptada.setUsuario(cuenta);
    Invitacion revocada = Cuentas.invitacion("revocada@correo.com", admin);
    revocada.setRevocadaEn(AHORA);
    revocada.setRevocadaPor(admin);
    repositorio.saveAllAndFlush(List.of(aceptada, revocada));
    em.clear();

    assertThat(repositorio.buscarAbierta("acepto@correo.com")).isEmpty();
    assertThat(repositorio.buscarAbierta("revocada@correo.com")).isEmpty();
  }

  @Test
  void persisteLaRevocacionYLaCuentaCreadaAlAceptar() {
    Usuario admin = usuarios.save(Cuentas.administrador("admin@correo.com"));
    Usuario cuenta = usuarios.save(Cuentas.estudiante("acepto@correo.com"));
    Invitacion aceptada = Cuentas.invitacion("acepto@correo.com", admin);
    aceptada.setAceptadaEn(AHORA);
    aceptada.setUsuario(cuenta);
    Invitacion revocada = Cuentas.invitacion("revocada@correo.com", admin);
    revocada.setRevocadaEn(AHORA);
    revocada.setRevocadaPor(admin);
    repositorio.saveAllAndFlush(List.of(aceptada, revocada));
    em.clear();

    Invitacion leidaAceptada = repositorio.findById(aceptada.getId()).orElseThrow();
    Invitacion leidaRevocada = repositorio.findById(revocada.getId()).orElseThrow();

    assertThat(leidaAceptada.getUsuario().getId()).isEqualTo(cuenta.getId());
    assertThat(leidaAceptada.getAceptadaEn()).isEqualTo(AHORA);
    assertThat(leidaRevocada.getRevocadaEn()).isEqualTo(AHORA);
    assertThat(leidaRevocada.getRevocadaPor().getId()).isEqualTo(admin.getId());
  }

  @Test
  void laBaseRechazaDosInvitacionesAbiertasParaElMismoCorreo() {
    Usuario admin = usuarios.save(Cuentas.administrador("admin@correo.com"));
    repositorio.saveAndFlush(Cuentas.invitacion("prof@correo.com", admin));
    Invitacion duplicada = Cuentas.invitacion("PROF@correo.com", admin);
    duplicada.setTokenHash(Cuentas.hash("duplicada"));

    assertThatThrownBy(() -> repositorio.saveAndFlush(duplicada))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("invitacion_abierta_uk");
  }

  @Test
  void buscaLaInvitacionPorElHashDeSuToken() {
    Usuario admin = usuarios.save(Cuentas.administrador("admin@correo.com"));
    Invitacion invitacion = repositorio.saveAndFlush(Cuentas.invitacion("prof@correo.com", admin));
    em.clear();

    assertThat(repositorio.findByTokenHash(Cuentas.hash("prof@correo.com")))
        .hasValueSatisfying(leida -> assertThat(leida.getId()).isEqualTo(invitacion.getId()));
    assertThat(repositorio.findByTokenHash(Cuentas.hash("otro"))).isEmpty();
    assertThat(repositorio.buscarPorHash(Cuentas.hash("prof@correo.com")))
        .hasValueSatisfying(leida -> assertThat(leida.getId()).isEqualTo(invitacion.getId()));
  }

  @Test
  void bloqueaLaInvitacionPorSuIdDentroDeLaTransaccion() {
    Usuario admin = usuarios.save(Cuentas.administrador("admin@correo.com"));
    Invitacion invitacion = repositorio.saveAndFlush(Cuentas.invitacion("prof@correo.com", admin));
    em.clear();

    assertThat(repositorio.bloquear(invitacion.getId()))
        .hasValueSatisfying(leida -> assertThat(leida.getCorreo()).isEqualTo("prof@correo.com"));
    assertThat(repositorio.bloquear(UUID.randomUUID())).isEmpty();
  }

  // DT-BE-08, DT-BE-09: página filtrada, con el administrador en la misma consulta que las filas
  @Test
  void paginaLasInvitacionesFiltradasConSuAdministradorSinConsultasN1() {
    List<Invitacion> invitaciones = new ArrayList<>();
    IntStream.rangeClosed(1, 6)
        .forEach(
            n -> {
              Usuario admin =
                  usuarios.save(Cuentas.administrador("jefe%d@correo.com".formatted(n)));
              invitaciones.add(Cuentas.invitacion("pagina%d@correo.com".formatted(n), admin));
            });
    repositorio.saveAllAndFlush(invitaciones);
    em.clear();
    Statistics estadisticas =
        em.getEntityManager()
            .getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
    estadisticas.clear();
    Specification<Invitacion> delFiltro =
        (raiz, consulta, cb) -> cb.like(raiz.get("correo"), "pagina%");

    Page<Invitacion> pagina =
        repositorio.findAll(delFiltro, PageRequest.of(0, 4, Sort.by("correo")));
    List<String> administradores =
        pagina.getContent().stream()
            .map(invitacion -> invitacion.getAdministrador().getCorreo())
            .toList();

    assertThat(pagina.getTotalElements()).isEqualTo(6);
    assertThat(administradores).hasSize(4).doesNotHaveDuplicates();
    // una para la página y otra para el total
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
  }

  // DT-BE-09: la cantidad de consultas no crece con la cantidad de invitaciones
  @Test
  void cargaLasInvitacionesConSuAdministradorSinConsultasN1() {
    List<Invitacion> invitaciones = new ArrayList<>();
    IntStream.rangeClosed(1, 20)
        .forEach(
            n -> {
              Usuario admin =
                  usuarios.save(Cuentas.administrador("admin%d@correo.com".formatted(n)));
              invitaciones.add(Cuentas.invitacion("invitado%d@correo.com".formatted(n), admin));
            });
    repositorio.saveAllAndFlush(invitaciones);
    em.clear();
    Statistics estadisticas =
        em.getEntityManager()
            .getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
    estadisticas.clear();

    List<String> administradores =
        repositorio.findAllByOrderByCreadoEnDesc().stream()
            .map(invitacion -> invitacion.getAdministrador().getCorreo())
            .toList();

    assertThat(administradores).hasSize(20).doesNotHaveDuplicates();
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }
}
