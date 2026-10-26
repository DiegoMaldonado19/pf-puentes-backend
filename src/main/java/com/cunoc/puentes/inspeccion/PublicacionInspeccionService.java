package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.inspeccion.puerto.CalculoIndice;
import com.cunoc.puentes.usuario.Rol;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestación de la publicación (US-P5-08): calcula el IC, transita por la puerta única, emite
 * {@link InspeccionPublicada} en la misma transacción. Entrada de P7: {@link #abrirRevision},
 * {@link #emitirVeredicto}, {@link #publicarPorAprobacion}.
 */
@Service
public class PublicacionInspeccionService {

  private final InspeccionRepository inspecciones;
  private final CalculoIndice calculo;
  private final CambioEstadoInspeccionService estados;
  private final ConsultaInspeccionService consultas;
  private final ApplicationEventPublisher eventos;

  PublicacionInspeccionService(
      InspeccionRepository inspecciones,
      CalculoIndice calculo,
      CambioEstadoInspeccionService estados,
      ConsultaInspeccionService consultas,
      ApplicationEventPublisher eventos) {
    this.inspecciones = inspecciones;
    this.calculo = calculo;
    this.estados = estados;
    this.consultas = consultas;
    this.eventos = eventos;
  }

  @Transactional
  public InspeccionDTO publicarDirecta(UUID inspeccionId, UUID actorId) {
    return publicar(inspeccionId, actorId, null, null, null);
  }

  @Transactional
  public InspeccionDTO publicarPorAprobacion(
      UUID inspeccionId,
      UUID revisorId,
      UUID revisionId,
      EstadoCondicion estadoConfirmado,
      String justificacion) {
    return publicar(inspeccionId, revisorId, revisionId, estadoConfirmado, justificacion);
  }

  @Transactional
  public void abrirRevision(UUID inspeccionId, UUID revisorId, UUID revisionId) {
    estados.cambiar(
        inspeccionId,
        EstadoInspeccion.EN_REVISION,
        revisorId,
        DatosTransicionInspeccion.de(null, revisionId));
  }

  @Transactional
  public InspeccionDTO emitirVeredicto(
      UUID inspeccionId,
      UUID revisorId,
      UUID revisionId,
      VeredictoRevision veredicto,
      String observacion,
      EstadoCondicion estadoConfirmado,
      String justificacion) {
    if (veredicto == null) {
      throw new IllegalArgumentException("El veredicto es obligatorio.");
    }
    return switch (veredicto) {
      case APROBADA ->
          publicarPorAprobacion(
              inspeccionId, revisorId, revisionId, estadoConfirmado, justificacion);
      case CAMBIOS_SOLICITADOS ->
          transitarVeredicto(
              inspeccionId,
              revisorId,
              revisionId,
              EstadoInspeccion.CAMBIOS_SOLICITADOS,
              observacion);
      case RECHAZADA ->
          transitarVeredicto(
              inspeccionId, revisorId, revisionId, EstadoInspeccion.RECHAZADA, observacion);
    };
  }

  private InspeccionDTO publicar(
      UUID inspeccionId,
      UUID actorId,
      UUID revisionId,
      EstadoCondicion estadoConfirmado,
      String justificacion) {
    Inspeccion inspeccion =
        inspecciones
            .findByIdAndEliminadoEnIsNull(inspeccionId)
            .orElseThrow(ConsultaInspeccionService::noEncontrada);
    if (!inspeccion.getEstado().permite(EstadoInspeccion.PUBLICADA)) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "transicion-invalida",
          "Transición de inspección no permitida",
          "La transición solicitada no está habilitada para el estado actual de la inspección.");
    }
    CalculoIndice.ResultadoCalculo ic =
        calculo.calcular(
            inspeccion.getDatos() == null ? Map.of() : inspeccion.getDatos(),
            inspeccion.getFormularioVersionId());
    EstadoCondicion confirmado = estadoConfirmado == null ? ic.estadoCalculado() : estadoConfirmado;
    UUID confirmadoPor = null;
    String justificacionGuardada = null;
    if (confirmado != ic.estadoCalculado()) {
      if (justificacion == null || justificacion.isBlank()) {
        throw new NegocioException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "justificacion-estado-requerida",
                "La justificación del estado es obligatoria",
                "Confirmar un estado distinto del calculado exige una justificación escrita.")
            .conErrores(
                List.of(
                    new ErrorCampo(
                        "justificacion_estado",
                        "Obligatoria cuando el estado confirmado difiere del calculado.")));
      }
      confirmadoPor = actorId;
      justificacionGuardada = justificacion.strip();
    }
    estados.cambiar(
        inspeccionId,
        EstadoInspeccion.PUBLICADA,
        actorId,
        DatosTransicionInspeccion.publicacion(
            revisionId,
            new DatosTransicionInspeccion.Publicacion(
                ic.indiceCondicion(),
                ic.estadoCalculado(),
                confirmado,
                ic.versionPesosId(),
                ic.anulacion(),
                confirmadoPor,
                justificacionGuardada)));
    eventos.publishEvent(
        new InspeccionPublicada(
            inspeccion.getId(),
            inspeccion.getPuenteId(),
            ic.indiceCondicion(),
            confirmado,
            inspeccion.getFechaInspeccion()));
    return consultas.detalle(inspeccionId, actorId, rolDe(actorId));
  }

  private InspeccionDTO transitarVeredicto(
      UUID inspeccionId,
      UUID revisorId,
      UUID revisionId,
      EstadoInspeccion destino,
      String observacion) {
    estados.cambiar(
        inspeccionId, destino, revisorId, DatosTransicionInspeccion.de(observacion, revisionId));
    return consultas.detalle(inspeccionId, revisorId, rolDe(revisorId));
  }

  private Rol rolDe(UUID actorId) {
    return inspecciones.rolDeUsuario(actorId).map(Rol::valueOf).orElse(Rol.CATEDRATICO);
  }
}
