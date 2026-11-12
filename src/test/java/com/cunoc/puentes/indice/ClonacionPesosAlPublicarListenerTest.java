package com.cunoc.puentes.indice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.formulario.FormularioPublicado;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClonacionPesosAlPublicarListenerTest {

  private static final UUID ANTERIOR = UUID.fromString("0192f5a0-0000-7000-8000-00000000000a");
  private static final UUID NUEVA = UUID.fromString("0192f5a0-0000-7000-8000-00000000000b");

  @Mock private VersionPesosRepository versiones;
  @Mock private PesoElementoRepository pesos;
  @Mock private MetadatosDanoFormulario metadatos;
  @Mock private ConfiguracionPesosService configuracion;

  private ClonacionPesosAlPublicarListener listener;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    listener = new ClonacionPesosAlPublicarListener(versiones, pesos, metadatos, configuracion);
  }

  @Test
  @DisplayName("Sin versión anterior activa no hay nada que copiar")
  void sinVersionAnteriorNoHaceNada() {
    listener.alPublicar(new FormularioPublicado(null, NUEVA));

    verifyNoInteractions(versiones, pesos, metadatos, configuracion);
  }

  @Test
  @DisplayName("Una versión con juego propio calibrado sobre el borrador no se sobrescribe")
  void respetaJuegoPropioDelBorrador() {
    when(versiones.existsByFormularioVersionId(NUEVA)).thenReturn(true);

    listener.alPublicar(new FormularioPublicado(ANTERIOR, NUEVA));

    verify(versiones, never()).findFirstByFormularioVersionIdOrderByVigenteDesdeDescIdDesc(any());
    verify(configuracion, never()).crear(any(), any());
  }

  @Test
  @DisplayName("Si la anterior no tiene juego vigente, la nueva queda sin pesos y no falla")
  void sinJuegoVigenteEnLaAnteriorNoClona() {
    when(versiones.existsByFormularioVersionId(NUEVA)).thenReturn(false);
    when(versiones.findFirstByFormularioVersionIdOrderByVigenteDesdeDescIdDesc(ANTERIOR))
        .thenReturn(Optional.empty());

    listener.alPublicar(new FormularioPublicado(ANTERIOR, NUEVA));

    verify(configuracion, never()).crear(any(), any());
  }

  @Test
  @DisplayName("Copia el juego vigente de la anterior a la nueva, con el mismo acta y pesos")
  void clonaElJuegoVigenteParaLaNuevaVersion() {
    VersionPesos origen = new VersionPesos();
    origen.setFormularioVersionId(ANTERIOR);
    origen.setActa("Acta 001/2026");
    ElementosDeDano elementos = new ElementosDeDano(NUEVA, Set.of("a.cimentacion", "b.apoyo"));

    when(versiones.existsByFormularioVersionId(NUEVA)).thenReturn(false);
    when(versiones.findFirstByFormularioVersionIdOrderByVigenteDesdeDescIdDesc(ANTERIOR))
        .thenReturn(Optional.of(origen));
    when(pesos.findByVersionPesosIdOrderByElementoRefAsc(any()))
        .thenReturn(List.of(peso("a.cimentacion", "1.500"), peso("b.apoyo", "2")));
    when(metadatos.obtener(NUEVA)).thenReturn(elementos);

    listener.alPublicar(new FormularioPublicado(ANTERIOR, NUEVA));

    ArgumentCaptor<JuegoPesos> juego = ArgumentCaptor.forClass(JuegoPesos.class);
    verify(configuracion).crear(juego.capture(), eq(elementos));
    assertThat(juego.getValue().formularioVersionId()).isEqualTo(NUEVA);
    assertThat(juego.getValue().acta()).isEqualTo("Acta 001/2026");
    assertThat(juego.getValue().pesos())
        .extracting(PesoConfigurado::elementoRef)
        .containsExactly("a.cimentacion", "b.apoyo");
  }

  @Test
  @DisplayName("Un juego que no cubre exactamente los campos de daño bloquea la publicación")
  void rechazaJuegoQueNoCubreLosCamposDeDano() {
    VersionPesos origen = new VersionPesos();
    origen.setFormularioVersionId(ANTERIOR);
    origen.setActa("Acta 001/2026");

    when(versiones.existsByFormularioVersionId(NUEVA)).thenReturn(false);
    when(versiones.findFirstByFormularioVersionIdOrderByVigenteDesdeDescIdDesc(ANTERIOR))
        .thenReturn(Optional.of(origen));
    when(pesos.findByVersionPesosIdOrderByElementoRefAsc(any()))
        .thenReturn(List.of(peso("a.cimentacion", "1")));
    when(metadatos.obtener(NUEVA))
        .thenReturn(new ElementosDeDano(NUEVA, Set.of("a.cimentacion", "b.apoyo")));

    assertThatThrownBy(() -> listener.alPublicar(new FormularioPublicado(ANTERIOR, NUEVA)))
        .isInstanceOf(NegocioException.class);
    verify(configuracion, never()).crear(any(), any());
  }

  private static PesoElemento peso(String referencia, String valor) {
    PesoElemento peso = new PesoElemento();
    peso.setElementoRef(referencia);
    peso.setPeso(new BigDecimal(valor));
    return peso;
  }
}
