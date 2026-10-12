package com.cunoc.puentes.usuario;

import static com.cunoc.puentes.usuario.Cuentas.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

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
