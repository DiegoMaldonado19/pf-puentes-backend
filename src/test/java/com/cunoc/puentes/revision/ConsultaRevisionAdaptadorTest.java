package com.cunoc.puentes.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.inspeccion.puerto.ResumenRevision;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsultaRevisionAdaptadorTest {

  @Mock ObservacionRevisionRepository observaciones;
  @Mock RevisionRepository revisiones;
  private ConsultaRevisionAdaptador adaptador;

  private final UUID conCambios = UUID.randomUUID();
  private final UUID aprobada = UUID.randomUUID();
  private final UUID sinRevision = UUID.randomUUID();

  @BeforeEach
  void preparar() {
    adaptador = new ConsultaRevisionAdaptador(observaciones, revisiones);
  }

  @Test
  void sinInspeccionesNoConsultaNada() {
    assertThat(adaptador.resumenes(List.of())).isEmpty();
    verifyNoInteractions(revisiones, observaciones);
  }

  @Test
  void usaLaUltimaRevisionEmitidaYCuentaSoloSusObservacionesPendientes() {
    Revision nueva =
        revision(conCambios, Veredicto.CAMBIOS_SOLICITADOS, Instant.parse("2026-11-10T10:00:00Z"));
    Revision vieja =
        revision(conCambios, Veredicto.CAMBIOS_SOLICITADOS, Instant.parse("2026-11-01T10:00:00Z"));
    Revision aprobacion =
        revision(aprobada, Veredicto.APROBADA, Instant.parse("2026-11-09T09:00:00Z"));
    List<ObservacionAnclada> pendientes = List.of(observacionDe(nueva), observacionDe(nueva));
    when(revisiones.findByInspeccionIdInAndVeredictoIsNotNullOrderByCreadoEnDesc(anyCollection()))
        .thenReturn(List.of(nueva, aprobacion, vieja));
    when(observaciones.findByRevision_IdInAndResueltaEnIsNull(anyCollection()))
        .thenReturn(pendientes);

    var resumenes = adaptador.resumenes(List.of(conCambios, aprobada, sinRevision));

    assertThat(resumenes)
        .containsOnlyKeys(conCambios, aprobada)
        .containsEntry(
            conCambios,
            new ResumenRevision("CAMBIOS_SOLICITADOS", Instant.parse("2026-11-10T10:00:00Z"), 2))
        .containsEntry(
            aprobada, new ResumenRevision("APROBADA", Instant.parse("2026-11-09T09:00:00Z"), 0));
  }

  @Test
  void sinRevisionesEmitidasNoPreguntaPorLasObservaciones() {
    when(revisiones.findByInspeccionIdInAndVeredictoIsNotNullOrderByCreadoEnDesc(anyCollection()))
        .thenReturn(List.of());

    assertThat(adaptador.resumenes(List.of(sinRevision))).isEmpty();
    verifyNoInteractions(observaciones);
  }

  private Revision revision(UUID inspeccion, Veredicto veredicto, Instant emitida) {
    Revision revision = mock(Revision.class);
    // Lenient: la revisión más antigua no entra en el resumen y no usa sus métodos
    lenient().when(revision.getId()).thenReturn(UUID.randomUUID());
    lenient().when(revision.getInspeccionId()).thenReturn(inspeccion);
    lenient().when(revision.getVeredicto()).thenReturn(veredicto);
    lenient().when(revision.getEmitidaEn()).thenReturn(emitida);
    return revision;
  }

  private ObservacionAnclada observacionDe(Revision revision) {
    ObservacionAnclada observacion = mock(ObservacionAnclada.class);
    when(observacion.getRevision()).thenReturn(revision);
    return observacion;
  }
}
