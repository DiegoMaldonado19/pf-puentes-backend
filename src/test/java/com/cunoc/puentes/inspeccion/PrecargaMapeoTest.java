package com.cunoc.puentes.inspeccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrecargaMapeoTest {

  @Test
  void consumeLaTraduccionAntesDeExtraerPermanentesYNoPrecargaDanos() {
    UUID puente = UUID.randomUUID();
    UUID autor = UUID.randomUUID();
    UUID anterior = UUID.randomUUID();
    UUID actual = UUID.randomUUID();
    Inspeccion borrador = new Inspeccion();
    borrador.setId(UUID.randomUUID());
    borrador.setPuenteId(puente);
    borrador.setFormularioVersionId(actual);
    Inspeccion origen = new Inspeccion();
    origen.setId(UUID.randomUUID());
    origen.setFormularioVersionId(anterior);
    origen.setDatos(Map.of("viejo", Map.of("longitud", 30, "dano", 4)));
    var repositorio = mock(InspeccionRepository.class);
    var contrato = mock(InspeccionService.class);
    var referencias = mock(ReferenciasInspeccion.class);
    var formularios = mock(ConsultaFormulario.class);
    when(contrato.propio(borrador.getId(), autor)).thenReturn(borrador);
    when(repositorio
            .findFirstByPuenteIdAndEstadoAndEliminadoEnIsNullOrderByFechaInspeccionDescPublicadaEnDesc(
                puente, EstadoInspeccion.PUBLICADA))
        .thenReturn(Optional.of(origen));
    when(formularios.traducir(origen.getDatos(), anterior, actual))
        .thenReturn(Map.of("nuevo", Map.of("longitud", 30, "dano", 4)));
    when(formularios.rutasPermanentes(actual)).thenReturn(Set.of("nuevo.longitud"));
    var servicio =
        new PrecargaInspeccionService(
            repositorio, contrato, referencias, formularios, null, null, null, null);

    var precarga = servicio.deInspeccion(borrador.getId(), autor);

    verify(formularios).traducir(origen.getDatos(), anterior, actual);
    assertThat(precarga.datos()).isEqualTo(Map.of("nuevo", Map.of("longitud", 30)));
    assertThat(precarga.origenInspeccionId()).isEqualTo(origen.getId());
    assertThat(origen.getDatos()).containsKey("viejo");
  }
}
