package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cunoc.puentes.PruebaIntegracion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Sql("/indice/formularios-pesos.sql")
class PesosRepositoryTest extends PruebaIntegracion {

  private static final UUID FORMULARIO_A = UUID.fromString("019a0000-0000-7000-8000-000000000001");
  private static final UUID FORMULARIO_B = UUID.fromString("019a0000-0000-7000-8000-000000000002");
  private static final UUID INEXISTENTE = UUID.fromString("019a0000-0000-7000-8000-000000000099");
  private static final Instant FECHA = Instant.parse("2026-10-18T12:00:00Z");

  @Autowired VersionPesosRepository versiones;
  @Autowired PesoElementoRepository pesos;
  @Autowired TestEntityManager em;

  @Test
  void guardaYRecuperaUnJuegoConSuFormularioActaYFechas() {
    VersionPesos guardada = juego(FORMULARIO_A, "actas/ejemplo-de-prueba.pdf");
    em.clear();

    VersionPesos leida = versiones.findById(guardada.getId()).orElseThrow();

    assertThat(leida.getId().version()).isEqualTo(7);
    assertThat(leida.getFormularioVersionId()).isEqualTo(FORMULARIO_A);
    assertThat(leida.getActa()).isEqualTo("actas/ejemplo-de-prueba.pdf");
    assertThat(leida.getVigenteDesde()).isEqualTo(FECHA);
    assertThat(leida.getCreadoEn()).isNotNull();
    assertThat(leida.getActualizadoEn()).isNotNull();
  }

  @Test
  void conservaUnJuegoProvisionalSinReferenciaAlActa() {
    VersionPesos juego = juego(FORMULARIO_A, null);
    em.clear();

    assertThat(versiones.findById(juego.getId()))
        .hasValueSatisfying(leido -> assertThat(leido.getActa()).isNull());
  }

  @Test
  void paginaLosPesosYPermiteConsultarSuJuegoSinCargasPerezosasAdicionales() {
    VersionPesos juego = juego(FORMULARIO_A, "actas/prueba.pdf");
    PesoElemento primero = peso(juego, "elemento-a", "0.001");
    peso(juego, "elemento-b", "999.999");
    em.clear();

    Page<PesoElemento> pagina = pesos.findByVersionPesosId(juego.getId(), pagina(0, 1));
    em.clear();

    assertThat(pagina.getTotalElements()).isEqualTo(2);
    assertThat(pagina.getTotalPages()).isEqualTo(2);
    PesoElemento leido = pagina.getContent().getFirst();
    assertThat(leido.getId()).isEqualTo(primero.getId());
    assertThat(leido.getId().version()).isEqualTo(7);
    assertThat(leido.getElementoRef()).isEqualTo("elemento-a");
    assertThat(leido.getPeso()).isEqualByComparingTo("0.001");
    assertThat(leido.getVersionPesos().getActa()).isEqualTo("actas/prueba.pdf");

    Page<PesoElemento> siguiente = pesos.findByVersionPesosId(juego.getId(), pagina(1, 1));
    assertThat(siguiente.getContent())
        .singleElement()
        .satisfies(
            elemento -> {
              assertThat(elemento.getElementoRef()).isEqualTo("elemento-b");
              assertThat(elemento.getPeso()).isEqualByComparingTo("999.999");
            });
  }

  @Test
  void dosJuegosDelMismoFormularioMantienenSusPropiosPesosYReferencias() {
    VersionPesos anterior = juego(FORMULARIO_A, "actas/anterior-prueba.pdf");
    peso(anterior, "elemento", "1.230");
    VersionPesos nuevo = juego(FORMULARIO_A, null);
    nuevo.setVigenteDesde(FECHA.plusSeconds(60));
    versiones.saveAndFlush(nuevo);
    peso(nuevo, "elemento", "3.500");
    em.clear();

    assertThat(nuevo.getId()).isNotEqualTo(anterior.getId());
    assertThat(versiones.findById(anterior.getId()))
        .hasValueSatisfying(
            leido -> {
              assertThat(leido.getActa()).isEqualTo("actas/anterior-prueba.pdf");
              assertThat(leido.getVigenteDesde()).isEqualTo(FECHA);
            });
    assertThat(versiones.findById(nuevo.getId())).isPresent();
    assertThat(pesos.findByVersionPesosId(anterior.getId(), pagina(0, 10)).getContent())
        .singleElement()
        .satisfies(elemento -> assertThat(elemento.getPeso()).isEqualByComparingTo("1.230"));
    assertThat(pesos.findByVersionPesosId(nuevo.getId(), pagina(0, 10)).getContent())
        .singleElement()
        .satisfies(elemento -> assertThat(elemento.getPeso()).isEqualByComparingTo("3.500"));
  }

  @Test
  void unaConsultaPorJuegoNoMezclaPesosDeOtrosFormularios() {
    VersionPesos a = juego(FORMULARIO_A, null);
    VersionPesos b = juego(FORMULARIO_B, null);
    peso(a, "elemento-a", "1");
    peso(b, "elemento-b", "2");
    em.clear();

    assertThat(pesos.findByVersionPesosId(a.getId(), pagina(0, 10)).getContent())
        .extracting(PesoElemento::getElementoRef)
        .containsExactly("elemento-a");
    assertThat(pesos.findByVersionPesosId(b.getId(), pagina(0, 10)).getContent())
        .extracting(PesoElemento::getElementoRef)
        .containsExactly("elemento-b");
  }

  @Test
  void unJuegoInexistenteNoDevuelvePesosDeOtroJuego() {
    peso(juego(FORMULARIO_A, null), "elemento", "1");
    em.clear();

    assertThat(versiones.findById(INEXISTENTE)).isEmpty();
    assertThat(pesos.findByVersionPesosId(INEXISTENTE, pagina(0, 10))).isEmpty();
  }

  @Test
  void postgresImpideDuplicarLaReferenciaDentroDelMismoJuego() {
    VersionPesos juego = juego(FORMULARIO_A, null);
    peso(juego, "elemento", "1");

    assertThatThrownBy(() -> peso(juego, "elemento", "2"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-0.001"})
  void postgresImpidePesosQueNoSonPositivos(String valor) {
    VersionPesos juego = juego(FORMULARIO_A, null);

    assertThatThrownBy(() -> peso(juego, "elemento", valor))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void postgresImpideUnJuegoSinFormularioExistente() {
    assertThatThrownBy(() -> juego(INEXISTENTE, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void postgresImpideUnPesoSinJuegoExistente() {
    VersionPesos inexistente = em.getEntityManager().getReference(VersionPesos.class, INEXISTENTE);

    assertThatThrownBy(() -> peso(inexistente, "elemento", "1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private VersionPesos juego(UUID formulario, String acta) {
    VersionPesos version = new VersionPesos();
    version.setFormularioVersionId(formulario);
    version.setActa(acta);
    version.setVigenteDesde(FECHA);
    return versiones.saveAndFlush(version);
  }

  private PesoElemento peso(VersionPesos version, String referencia, String valor) {
    PesoElemento elemento = new PesoElemento();
    elemento.setVersionPesos(version);
    elemento.setElementoRef(referencia);
    elemento.setPeso(new BigDecimal(valor));
    return pesos.saveAndFlush(elemento);
  }

  private static PageRequest pagina(int numero, int tamano) {
    return PageRequest.of(numero, tamano, Sort.by("elementoRef"));
  }
}
