package com.cunoc.puentes.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Verifica el contrato JSON con el mapper generado y la configuración Jackson de Spring Boot. */
@JsonTest
@Import(OrdenMantenimientoMapperImpl.class)
class OrdenMantenimientoDTOTest {

  private static final UUID ID = UUID.fromString("019a0024-0000-7000-8000-000000000001");
  private static final UUID PUENTE = UUID.fromString("019a0024-0000-7000-8000-000000000002");
  private static final UUID INSPECCION = UUID.fromString("019a0024-0000-7000-8000-000000000003");
  private static final Instant CREADO = Instant.parse("2026-10-19T01:00:00Z");
  private static final DatosTransicionOrden SIN_DATOS =
      new DatosTransicionOrden(null, null, 0, null);

  @Autowired OrdenMantenimientoMapper mapper;
  @Autowired JsonMapper json;

  @Test
  void devuelveElContratoExplicitoSinExponerLaEntidadYConFechasUtc() {
    OrdenMantenimiento orden = orden();
    orden.setInspeccionId(INSPECCION);
    orden.setElementoRef("elemento-de-prueba");
    orden.setPrioridadSugerida(Prioridad.MEDIA);
    orden.setPrioridad(Prioridad.ALTA);
    orden.setJustificacionPrioridad("Cambio justificado");
    orden.setFechaProgramada(LocalDate.of(2026, 10, 19));
    orden.cambiarEstado(EstadoOrden.PROGRAMADA, SIN_DATOS);
    orden.cambiarEstado(EstadoOrden.EN_EJECUCION, SIN_DATOS);
    orden.cambiarEstado(
        EstadoOrden.EJECUTADA,
        new DatosTransicionOrden(LocalDate.of(2026, 10, 20), "Responsable de prueba", 1, null));

    OrdenMantenimientoDTO dto = mapper.aDTO(orden);
    Map<String, Object> contenido = contenido(dto);

    assertThat(contenido)
        .containsOnlyKeys(
            "id",
            "puente_id",
            "inspeccion_id",
            "tipo",
            "estado",
            "descripcion",
            "elemento_ref",
            "prioridad_sugerida",
            "prioridad",
            "justificacion_prioridad",
            "fecha_programada",
            "fecha_ejecucion",
            "responsable",
            "motivo",
            "creado_en",
            "actualizado_en")
        .containsEntry("id", ID.toString())
        .containsEntry("puente_id", PUENTE.toString())
        .containsEntry("inspeccion_id", INSPECCION.toString())
        .containsEntry("tipo", "CORRECTIVO")
        .containsEntry("estado", "EJECUTADA")
        .containsEntry("descripcion", "Intervención de prueba")
        .containsEntry("elemento_ref", "elemento-de-prueba")
        .containsEntry("prioridad_sugerida", "MEDIA")
        .containsEntry("prioridad", "ALTA")
        .containsEntry("justificacion_prioridad", "Cambio justificado")
        .containsEntry("fecha_programada", "2026-10-19")
        .containsEntry("fecha_ejecucion", "2026-10-20")
        .containsEntry("responsable", "Responsable de prueba")
        .containsEntry("creado_en", "2026-10-19T01:00:00Z")
        .containsEntry("actualizado_en", "2026-10-19T01:01:00Z");
    assertThat(orden.getEstado()).isEqualTo(EstadoOrden.EJECUTADA);
  }

  @Test
  void mantieneAusenciasComoNullSinInventarOrigenPrioridadSugeridaOFechasDeCierre() {
    Map<String, Object> contenido = contenido(mapper.aDTO(orden()));

    assertThat(contenido)
        .containsEntry("estado", "PROPUESTA")
        .containsEntry("prioridad", "MEDIA")
        .containsEntry("inspeccion_id", null)
        .containsEntry("prioridad_sugerida", null)
        .containsEntry("justificacion_prioridad", null)
        .containsEntry("fecha_programada", null)
        .containsEntry("fecha_ejecucion", null)
        .containsEntry("responsable", null)
        .containsEntry("motivo", null);
  }

  @Test
  void conservaElMotivoDeUnaOrdenDescartada() {
    OrdenMantenimiento orden = orden();
    orden.cambiarEstado(
        EstadoOrden.DESCARTADA,
        new DatosTransicionOrden(null, null, 0, "La intervención no corresponde"));

    assertThat(contenido(mapper.aDTO(orden)))
        .containsEntry("estado", "DESCARTADA")
        .containsEntry("motivo", "La intervención no corresponde");
  }

  private Map<String, Object> contenido(OrdenMantenimientoDTO dto) {
    return json.readValue(json.writeValueAsString(dto), new TypeReference<>() {});
  }

  private static OrdenMantenimiento orden() {
    OrdenMantenimiento orden = new OrdenMantenimiento();
    // Identidad y timestamps de una entidad hidratada, sin añadir setters al modelo productivo.
    ReflectionTestUtils.setField(orden, "id", ID);
    ReflectionTestUtils.setField(orden, "creadoEn", CREADO);
    ReflectionTestUtils.setField(orden, "actualizadoEn", CREADO.plusSeconds(60));
    orden.setPuenteId(PUENTE);
    orden.setTipo(TipoOrden.CORRECTIVO);
    orden.setDescripcion("Intervención de prueba");
    orden.setPrioridad(Prioridad.MEDIA);
    return orden;
  }
}
