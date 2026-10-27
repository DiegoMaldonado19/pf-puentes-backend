package com.cunoc.puentes.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.PublicacionInspeccionService;
import com.cunoc.puentes.usuario.EstadoCuentaService;
import com.cunoc.puentes.usuario.Rol;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RevisionServiceTest {
  @Mock RevisionRepository revisiones;
  @Mock BandejaRevisionRepository bandeja;
  @Mock PublicacionInspeccionService inspecciones;
  @Mock EstadoCuentaService cuentas;
  private RevisionService servicio;
  private final UUID actor = UUID.randomUUID();
  private final UUID inspeccion = UUID.randomUUID();

  @BeforeEach
  void preparar() {
    servicio = new RevisionService(revisiones, bandeja, inspecciones, cuentas);
  }

  @Test
  void exigeCuentaActivaYRolEnAmbasOperaciones() {
    when(cuentas.rolSiEstaActiva(actor)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.abrir(actor, inspeccion))
        .isInstanceOf(NegocioException.class);
    when(cuentas.rolSiEstaActiva(actor)).thenReturn(Optional.of(Rol.ESTUDIANTE));
    assertThatThrownBy(() -> servicio.pendientes(actor, null, PageRequest.of(0, 10)))
        .isInstanceOf(NegocioException.class);
    verifyNoInteractions(bandeja, revisiones, inspecciones);
  }

  @Test
  void validaFiltroAntesDeConsultar() {
    when(cuentas.rolSiEstaActiva(actor)).thenReturn(Optional.of(Rol.CATEDRATICO));
    assertThatThrownBy(() -> servicio.pendientes(actor, "BORRADOR", PageRequest.of(0, 10)))
        .isInstanceOf(NegocioException.class);
    verifyNoInteractions(bandeja);
  }

  @Test
  void acotaTamanoSinCambiarPagina() {
    when(cuentas.rolSiEstaActiva(actor)).thenReturn(Optional.of(Rol.CATEDRATICO));
    when(bandeja.pendientes(actor, null, PageRequest.of(2, 100))).thenReturn(Page.empty());
    assertThat(servicio.pendientes(actor, null, PageRequest.of(2, 200))).isEmpty();
    verify(bandeja).pendientes(actor, null, PageRequest.of(2, 100));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ENVIADA", "EN_REVISION"})
  void aceptaLosEstadosDeLaBandeja(String estado) {
    when(cuentas.rolSiEstaActiva(actor)).thenReturn(Optional.of(Rol.CATEDRATICO));
    when(bandeja.pendientes(actor, estado, PageRequest.of(0, 10))).thenReturn(Page.empty());
    assertThat(servicio.pendientes(actor, estado, PageRequest.of(0, 10))).isEmpty();
  }

  @Test
  void deniegaInspeccionNoAutorizadaSinCrearCiclo() {
    when(cuentas.rolSiEstaActiva(actor)).thenReturn(Optional.of(Rol.CATEDRATICO));
    when(bandeja.bloquear(actor, inspeccion)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.abrir(actor, inspeccion))
        .isInstanceOf(NegocioException.class);
    verifyNoInteractions(revisiones, inspecciones);
  }

  @Test
  void deniegaOtroEstadoSinCrearCiclo() {
    when(cuentas.rolSiEstaActiva(actor)).thenReturn(Optional.of(Rol.CATEDRATICO));
    when(bandeja.bloquear(actor, inspeccion)).thenReturn(Optional.of("EN_REVISION"));
    assertThatThrownBy(() -> servicio.abrir(actor, inspeccion))
        .isInstanceOf(NegocioException.class);
    verifyNoInteractions(revisiones, inspecciones);
  }

  @Test
  void noDuplicaCicloAunqueLaInspeccionInconsistenteSigaEnviada() {
    when(cuentas.rolSiEstaActiva(actor)).thenReturn(Optional.of(Rol.CATEDRATICO));
    when(bandeja.bloquear(actor, inspeccion)).thenReturn(Optional.of("ENVIADA"));
    when(revisiones.findByInspeccionIdAndVeredictoIsNull(inspeccion))
        .thenReturn(Optional.of(new Revision()));
    assertThatThrownBy(() -> servicio.abrir(actor, inspeccion))
        .isInstanceOf(NegocioException.class);
    verifyNoInteractions(inspecciones);
  }

  @Test
  void delegaAperturaSinMutarEstadoDirectamente() {
    when(cuentas.rolSiEstaActiva(actor)).thenReturn(Optional.of(Rol.CATEDRATICO));
    when(bandeja.bloquear(actor, inspeccion)).thenReturn(Optional.of("ENVIADA"));
    when(revisiones.findByInspeccionIdAndVeredictoIsNull(inspeccion)).thenReturn(Optional.empty());
    when(revisiones.saveAndFlush(any(Revision.class)))
        .thenAnswer(invocacion -> invocacion.getArgument(0));
    var resultado = servicio.abrir(actor, inspeccion);
    var captor = ArgumentCaptor.forClass(Revision.class);
    verify(revisiones).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getInspeccionId()).isEqualTo(inspeccion);
    assertThat(captor.getValue().getRevisorId()).isEqualTo(actor);
    assertThat(resultado.inspeccionId()).isEqualTo(inspeccion);
    verify(inspecciones).abrirRevision(inspeccion, actor, resultado.id());
  }
}
