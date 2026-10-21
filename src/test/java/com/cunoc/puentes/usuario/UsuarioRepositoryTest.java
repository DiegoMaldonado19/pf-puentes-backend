package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.usuario.Cuentas.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
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
class UsuarioRepositoryTest extends PruebaIntegracion {

  @Autowired UsuarioRepository repositorio;

  @Autowired TestEntityManager em;

  @Test
  void persisteYLeeUnaBajaLogicaCompleta() {
    Usuario administrador = repositorio.save(Cuentas.administrador("admin@correo.com"));
    Usuario estudiante = Cuentas.estudiante("estudiante@correo.com");
    estudiante.setEstado(EstadoUsuario.INACTIVO);
    estudiante.setDesactivadoEn(AHORA);
    estudiante.setDesactivadoPor(administrador);
    estudiante.setMotivoBaja("Ya no pertenece al curso");
    Usuario guardado = repositorio.saveAndFlush(estudiante);
    em.clear();

    Usuario leido = repositorio.findById(guardado.getId()).orElseThrow();

    assertThat(leido.getEstado()).isEqualTo(EstadoUsuario.INACTIVO);
    assertThat(leido.getDesactivadoEn()).isEqualTo(AHORA);
    assertThat(leido.getDesactivadoPor().getId()).isEqualTo(administrador.getId());
    assertThat(leido.getMotivoBaja()).isEqualTo("Ya no pertenece al curso");
  }

  @Test
  void persisteQuienVerificoElColegiado() {
    Usuario administrador = repositorio.save(Cuentas.administrador("admin@correo.com"));
    Usuario profesional = Cuentas.estudiante("profesional@correo.com");
    profesional.setRol(Rol.PROFESIONAL_EXTERNO);
    profesional.setNumeroColegiado("1234");
    profesional.setColegiadoVerificadoEn(AHORA);
    profesional.setColegiadoVerificadoPor(administrador);
    Usuario guardado = repositorio.saveAndFlush(profesional);
    em.clear();

    Usuario leido = repositorio.findById(guardado.getId()).orElseThrow();

    assertThat(leido.getColegiadoVerificadoEn()).isEqualTo(AHORA);
    assertThat(leido.getColegiadoVerificadoPor().getId()).isEqualTo(administrador.getId());
  }

  @Test
  void reactivarLimpiaLosCamposDeBajaEnLaMismaOperacion() {
    Usuario estudiante = Cuentas.estudiante("estudiante@correo.com");
    estudiante.setEstado(EstadoUsuario.INACTIVO);
    estudiante.setDesactivadoEn(AHORA);
    estudiante.setMotivoBaja("Baja de prueba");
    repositorio.saveAndFlush(estudiante);

    estudiante.setEstado(EstadoUsuario.ACTIVO);
    estudiante.setDesactivadoEn(null);
    estudiante.setMotivoBaja(null);
    repositorio.saveAndFlush(estudiante);
    em.clear();

    Usuario leido = repositorio.findById(estudiante.getId()).orElseThrow();
    assertThat(leido.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
    assertThat(leido.getDesactivadoEn()).isNull();
    assertThat(leido.getMotivoBaja()).isNull();
  }

  @Test
  void buscaPorCorreoSinDistinguirMayusculas() {
    Usuario ana = repositorio.saveAndFlush(Cuentas.estudiante("Ana.Perez@Correo.com"));
    repositorio.saveAndFlush(Cuentas.estudiante("otra@correo.com"));
    em.clear();

    assertThat(repositorio.buscarPorCorreo("ana.perez@correo.com"))
        .hasValueSatisfying(usuario -> assertThat(usuario.getId()).isEqualTo(ana.getId()));
    assertThat(repositorio.buscarPorCorreo("nadie@correo.com")).isEmpty();
  }

  @Test
  void detectaSiUnCorreoYaEstaRegistrado() {
    repositorio.saveAndFlush(Cuentas.estudiante("ana@correo.com"));

    assertThat(repositorio.existePorCorreo("ANA@correo.com")).isTrue();
    assertThat(repositorio.existePorCorreo("otra@correo.com")).isFalse();
  }

  @Test
  void soloUnaCuentaActivaTieneRolVigente() {
    Usuario activa = repositorio.saveAndFlush(Cuentas.estudiante("activa@correo.com"));
    Usuario pendiente = Cuentas.estudiante("pendiente@correo.com");
    pendiente.setEstado(EstadoUsuario.PENDIENTE);
    pendiente.setCorreoVerificadoEn(null);
    repositorio.saveAndFlush(pendiente);
    Usuario inactiva = Cuentas.estudiante("inactiva@correo.com");
    inactiva.setEstado(EstadoUsuario.INACTIVO);
    inactiva.setDesactivadoEn(AHORA);
    inactiva.setMotivoBaja("Baja de prueba");
    repositorio.saveAndFlush(inactiva);
    em.clear();

    assertThat(repositorio.buscarRolActivo(activa.getId())).contains(Rol.ESTUDIANTE);
    assertThat(repositorio.buscarRolActivo(pendiente.getId())).isEmpty();
    assertThat(repositorio.buscarRolActivo(inactiva.getId())).isEmpty();
    assertThat(repositorio.buscarRolActivo(UUID.randomUUID())).isEmpty();
  }

  @Test
  void bloqueaLaCuentaPorSuIdDentroDeLaTransaccion() {
    Usuario ana = repositorio.saveAndFlush(Cuentas.estudiante("ana@correo.com"));
    em.clear();

    assertThat(repositorio.bloquear(ana.getId()))
        .hasValueSatisfying(usuario -> assertThat(usuario.getCorreo()).isEqualTo("ana@correo.com"));
    assertThat(repositorio.bloquear(UUID.randomUUID())).isEmpty();
  }

  @Test
  void cuentaSoloLasCuentasDelRolEnElEstadoPedido() {
    long activosAntes = repositorio.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoUsuario.ACTIVO);
    long inactivosAntes =
        repositorio.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoUsuario.INACTIVO);
    repositorio.saveAndFlush(Cuentas.administrador("admin1@correo.com"));
    repositorio.saveAndFlush(Cuentas.administrador("admin2@correo.com"));
    Usuario inactivo = Cuentas.administrador("admin3@correo.com");
    inactivo.setEstado(EstadoUsuario.INACTIVO);
    inactivo.setDesactivadoEn(AHORA);
    inactivo.setMotivoBaja("Baja de prueba");
    repositorio.saveAndFlush(inactivo);
    repositorio.saveAndFlush(Cuentas.estudiante("estudiante@correo.com"));

    long activos = repositorio.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoUsuario.ACTIVO);
    long inactivos = repositorio.countByRolAndEstado(Rol.ADMINISTRADOR, EstadoUsuario.INACTIVO);

    assertThat(activos - activosAntes).isEqualTo(2);
    assertThat(inactivos - inactivosAntes).isEqualTo(1);
  }

  @Test
  void filtraYPaginaConEspecificaciones() {
    repositorio.saveAndFlush(Cuentas.estudiante("filtro1@correo.com"));
    repositorio.saveAndFlush(Cuentas.estudiante("filtro2@correo.com"));
    repositorio.saveAndFlush(Cuentas.administrador("filtro3@correo.com"));
    em.clear();
    Specification<Usuario> estudiantesDelFiltro =
        (raiz, consulta, cb) ->
            cb.and(
                cb.equal(raiz.get("rol"), Rol.ESTUDIANTE), cb.like(raiz.get("correo"), "filtro%"));

    Page<Usuario> pagina =
        repositorio.findAll(estudiantesDelFiltro, PageRequest.of(0, 1, Sort.by("correo")));

    assertThat(pagina.getTotalElements()).isEqualTo(2);
    assertThat(pagina.getContent())
        .singleElement()
        .extracting(Usuario::getCorreo)
        .isEqualTo("filtro1@correo.com");
  }

  @Test
  void laBaseRechazaUnaCuentaInactivaSinFechaNiMotivoDeBaja() {
    Usuario incoherente = Cuentas.estudiante("incoherente@correo.com");
    incoherente.setEstado(EstadoUsuario.INACTIVO);

    assertThatThrownBy(() -> repositorio.saveAndFlush(incoherente))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("usuario_baja_ck");
  }

  @Test
  void laBaseRechazaUnaCuentaActivaSinCorreoVerificado() {
    Usuario sinVerificar = Cuentas.estudiante("sin-verificar@correo.com");
    sinVerificar.setCorreoVerificadoEn(null);

    assertThatThrownBy(() -> repositorio.saveAndFlush(sinVerificar))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("usuario_activo_correo_ck");
  }

  // Notas de US-P2-01: rol, estado y contadores los cambian solo los servicios de usuario/
  @Test
  void soloElPaqueteCambiaElRolElEstadoYLosContadoresDeBloqueo() {
    List<String> restringidos =
        List.of(
            "setRol", "setEstado", "setIntentosFallidos", "setPrimerFalloEn", "setBloqueadoHasta");

    for (String nombre : restringidos) {
      assertThat(Modifier.isPublic(setter(nombre).getModifiers())).as(nombre).isFalse();
    }
    assertThat(Modifier.isPublic(setter("setDesactivadoEn").getModifiers())).isTrue();
  }

  private static Method setter(String nombre) {
    return Arrays.stream(Usuario.class.getDeclaredMethods())
        .filter(metodo -> metodo.getName().equals(nombre))
        .findFirst()
        .orElseThrow();
  }
}
