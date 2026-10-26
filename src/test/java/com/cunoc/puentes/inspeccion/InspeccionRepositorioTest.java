package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.indice.EstadoCondicion;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Sql("/inspeccion/modelo.sql")
class InspeccionRepositorioTest extends PruebaIntegracion {

  private static final UUID PUENTE_A = InspeccionModeloTest.PUENTE_A;
  private static final UUID AUTOR = InspeccionModeloTest.AUTOR;
  private static final UUID AUTOR_2 = UUID.fromString("0199c000-0000-7000-8000-000000000012");
  private static final UUID FORMULARIO = InspeccionModeloTest.FORMULARIO;
  private static final UUID CURSO = InspeccionModeloTest.CURSO;

  @Autowired InspeccionRepository inspecciones;
  @Autowired EntityManager em;

  @Test
  void consultaPorAutorPuenteCursoEstadoBorradoresInactivosCorreccionYUltimaPublicada() {
    Inspeccion publicadaAntigua = publicada("0000000000a1", LocalDate.of(2026, 1, 1), AUTOR);
    Inspeccion publicadaReciente = publicada("0000000000a2", LocalDate.of(2026, 6, 1), AUTOR);
    Inspeccion enviada = borrador("0000000000a3", AUTOR, LocalDate.of(2026, 10, 1));
    enviada.setEstado(EstadoInspeccion.ENVIADA);
    enviada.setCursoId(CURSO);
    Inspeccion borradorPropio = borrador("0000000000a4", AUTOR, LocalDate.of(2026, 10, 2));
    borradorPropio.setCursoId(CURSO);
    Inspeccion inactivo = borrador("0000000000a5", AUTOR, LocalDate.of(2026, 3, 1));
    inactivo.setUltimaActividadEn(Instant.parse("2026-01-01T00:00:00Z"));
    Inspeccion deOtro = borrador("0000000000a6", AUTOR_2, LocalDate.of(2026, 10, 3));
    inspecciones.save(publicadaAntigua);
    inspecciones.save(publicadaReciente);
    inspecciones.save(enviada);
    inspecciones.save(borradorPropio);
    inspecciones.save(inactivo);
    inspecciones.save(deOtro);
    Inspeccion correccion = borrador("0000000000a7", AUTOR, LocalDate.of(2026, 10, 4));
    correccion.setSupersedeA(publicadaReciente);
    correccion.setMotivoCorreccion("profundidad del estribo");
    inspecciones.save(correccion);
    em.flush();
    em.clear();

    assertThat(
            inspecciones
                .findByAutorIdAndEliminadoEnIsNullOrderByCreadoEnDesc(AUTOR, PageRequest.of(0, 10))
                .getContent())
        .extracting(Inspeccion::getId)
        .contains(publicadaAntigua.getId(), enviada.getId())
        .doesNotContain(deOtro.getId());
    assertThat(inspecciones.findByPuenteIdAndEliminadoEnIsNull(PUENTE_A))
        .extracting(Inspeccion::getId)
        .contains(publicadaAntigua.getId(), enviada.getId(), deOtro.getId());
    assertThat(
            inspecciones.findByPuenteIdAndEstadoAndEliminadoEnIsNull(
                PUENTE_A, EstadoInspeccion.PUBLICADA))
        .extracting(Inspeccion::getId)
        .containsExactlyInAnyOrder(publicadaAntigua.getId(), publicadaReciente.getId());
    assertThat(inspecciones.findByCursoIdAndEstado(CURSO, EstadoInspeccion.ENVIADA))
        .extracting(Inspeccion::getId)
        .containsExactly(enviada.getId());
    assertThat(inspecciones.findBorradoresInactivosSinAviso(Instant.parse("2026-04-01T00:00:00Z")))
        .extracting(Inspeccion::getId)
        .contains(inactivo.getId())
        .doesNotContain(borradorPropio.getId());
    assertThat(
            inspecciones.findBySupersedeA_IdAndEliminadoEnIsNullAndEstadoNot(
                publicadaReciente.getId(), EstadoInspeccion.RECHAZADA))
        .get()
        .extracting(Inspeccion::getId)
        .isEqualTo(correccion.getId());
    assertThat(
            inspecciones
                .findFirstByPuenteIdAndEstadoAndEliminadoEnIsNullOrderByFechaInspeccionDescPublicadaEnDesc(
                    PUENTE_A, EstadoInspeccion.PUBLICADA))
        .get()
        .extracting(Inspeccion::getId)
        .isEqualTo(publicadaReciente.getId());
  }

  @Test
  void listaSinConsultasN1AunqueHayaTreintaInspecciones() {
    IntStream.rangeClosed(1, 30)
        .forEach(
            n ->
                inspecciones.save(
                    borrador("0000000001%02d".formatted(n), AUTOR, LocalDate.of(2026, 10, 1))));
    em.flush();
    em.clear();
    Statistics estadisticas =
        em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    estadisticas.clear();

    var listadas = inspecciones.findByAutorIdAndEliminadoEnIsNullOrderByCreadoEnDesc(AUTOR);
    listadas.forEach(
        inspeccion -> {
          inspeccion.getPuenteId();
          inspeccion.getSupersedeA();
        });

    assertThat(listadas).hasSize(30);
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  private Inspeccion borrador(String sufijo, UUID autor, LocalDate fecha) {
    Inspeccion inspeccion = new Inspeccion();
    inspeccion.setId(id(sufijo));
    inspeccion.setPuenteId(PUENTE_A);
    inspeccion.setAutorId(autor);
    inspeccion.setFormularioVersionId(FORMULARIO);
    inspeccion.setFechaInspeccion(fecha);
    return inspeccion;
  }

  private Inspeccion publicada(String sufijo, LocalDate fecha, UUID autor) {
    Inspeccion inspeccion = borrador(sufijo, autor, fecha);
    inspeccion.setEstado(EstadoInspeccion.PUBLICADA);
    inspeccion.setIndiceCondicion(new BigDecimal("75.00"));
    inspeccion.setEstadoCalculado(EstadoCondicion.BUENO);
    inspeccion.setEstadoConfirmado(EstadoCondicion.BUENO);
    inspeccion.setPublicadaEn(Instant.parse("2026-10-01T00:00:00Z"));
    return inspeccion;
  }

  private static UUID id(String sufijo) {
    return UUID.fromString("0199c000-0000-7000-8000-" + sufijo);
  }
}
