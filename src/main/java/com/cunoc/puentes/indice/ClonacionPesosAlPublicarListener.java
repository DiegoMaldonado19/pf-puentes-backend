package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.formulario.FormularioPublicado;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * B2: al publicar una versión, la nueva recibe una copia del juego de pesos vigente de la versión
 * anterior, en la misma transacción. Sin ese juego, las inspecciones nuevas no calculan IC, no se
 * publican y no generan órdenes, porque el cálculo busca los pesos por versión.
 *
 * <p>Si la versión ya tiene un juego propio (calibrado sobre el borrador), se respeta y no se copia
 * nada. Si el juego anterior no cubre exactamente los campos de daño de la nueva versión, la
 * publicación se rechaza: así no queda una versión activa sin IC calculable.
 */
@Component
class ClonacionPesosAlPublicarListener {

  private final VersionPesosRepository versiones;
  private final PesoElementoRepository pesos;
  private final MetadatosDanoFormulario metadatos;
  private final ConfiguracionPesosService configuracion;

  ClonacionPesosAlPublicarListener(
      VersionPesosRepository versiones,
      PesoElementoRepository pesos,
      MetadatosDanoFormulario metadatos,
      ConfiguracionPesosService configuracion) {
    this.versiones = versiones;
    this.pesos = pesos;
    this.metadatos = metadatos;
    this.configuracion = configuracion;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void alPublicar(FormularioPublicado evento) {
    if (evento.anteriorId() == null || versiones.existsByFormularioVersionId(evento.nuevoId())) {
      return;
    }
    versiones
        .findFirstByFormularioVersionIdOrderByVigenteDesdeDescIdDesc(evento.anteriorId())
        .ifPresent(vigente -> clonar(vigente, evento.nuevoId()));
  }

  private void clonar(VersionPesos origen, UUID destino) {
    List<PesoConfigurado> copia =
        pesos.findByVersionPesosIdOrderByElementoRefAsc(origen.getId()).stream()
            .map(peso -> new PesoConfigurado(peso.getElementoRef(), peso.getPeso()))
            .toList();
    ElementosDeDano elementos = metadatos.obtener(destino);
    Set<String> referencias =
        copia.stream().map(PesoConfigurado::elementoRef).collect(Collectors.toSet());
    if (!referencias.equals(elementos.referencias())) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "juego-pesos-incompleto",
          "Pesos incompatibles con la nueva versión",
          "El juego vigente de la versión anterior no cubre exactamente los campos de daño de"
              + " esta versión. Cree un juego de pesos para el borrador antes de publicarlo.");
    }
    configuracion.crear(new JuegoPesos(destino, origen.getActa(), copia), elementos);
  }
}
