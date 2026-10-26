package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.archivo.ArchivoService;
import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.dto.EnviarInspeccionDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import com.cunoc.puentes.sync.IdempotenciaService;
import com.cunoc.puentes.sync.RespuestaIdempotente;
import com.cunoc.puentes.usuario.Rol;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class EnvioInspeccionService {

  static final int MIN_FOTOS = 4;
  private static final Duration ESPERA_IDEMPOTENCIA = Duration.ofSeconds(2);

  private final InspeccionService contrato;
  private final InspeccionRepository inspecciones;
  private final ConsultaFormulario formularios;
  private final ValidacionFechaInspeccion fechas;
  private final ReferenciasInspeccion referencias;
  private final ArchivoService archivos;
  private final CambioEstadoInspeccionService estados;
  private final ConsultaInspeccionService consultas;
  private final AvisoInspeccionEnviada avisos;
  private final PublicacionInspeccionService publicacion;
  private final IdempotenciaService idempotencia;
  private final JsonMapper json;

  EnvioInspeccionService(
      InspeccionService contrato,
      InspeccionRepository inspecciones,
      ConsultaFormulario formularios,
      ValidacionFechaInspeccion fechas,
      ReferenciasInspeccion referencias,
      ArchivoService archivos,
      CambioEstadoInspeccionService estados,
      ConsultaInspeccionService consultas,
      AvisoInspeccionEnviada avisos,
      PublicacionInspeccionService publicacion,
      IdempotenciaService idempotencia,
      JsonMapper json) {
    this.contrato = contrato;
    this.inspecciones = inspecciones;
    this.formularios = formularios;
    this.fechas = fechas;
    this.referencias = referencias;
    this.archivos = archivos;
    this.estados = estados;
    this.consultas = consultas;
    this.avisos = avisos;
    this.publicacion = publicacion;
    this.idempotencia = idempotencia;
    this.json = json;
  }

  @Transactional
  InspeccionDTO enviar(
      UUID inspeccionId,
      UUID actorId,
      Rol rol,
      EnviarInspeccionDTO cuerpo,
      String claveIdempotencia,
      boolean sync) {
    return ejecutarEnvio(inspeccionId, actorId, rol, cuerpo);
  }

  RespuestaIdempotente enviarIdempotente(
      UUID inspeccionId,
      UUID actorId,
      Rol rol,
      EnviarInspeccionDTO cuerpo,
      String claveIdempotencia,
      boolean sync) {
    // Incluso el replay exige un recurso propio y no eliminado.
    contrato.propio(inspeccionId, actorId);
    String clave = claveIdempotencia.strip();
    if (clave.codePointCount(0, clave.length()) > 100) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "idempotencia-invalida",
          "Clave de idempotencia inválida",
          "Idempotency-Key no puede superar 100 caracteres.");
    }
    return idempotencia.ejecutar(
        actorId,
        clave,
        HuellaEnvioIdempotencia.crear(inspeccionId, sync, cuerpo, json),
        ESPERA_IDEMPOTENCIA,
        () -> {
          // La autorización y validación se repiten dentro de la transacción del núcleo.
          Inspeccion inspeccion = contrato.propio(inspeccionId, actorId);
          if (inspeccion.getEstado() != EstadoInspeccion.ENVIADA) {
            inspeccion.exigirEditable();
            try {
              exigirPublicable(inspeccion);
            } catch (NegocioException rechazo) {
              if (rechazo.getStatusCode().value() != 422
                  || !rechazo.getBody().getType().getPath().endsWith("/inspeccion-no-publicable")) {
                throw rechazo;
              }
              rechazo
                  .getBody()
                  .setInstance(URI.create("/api/v1/inspecciones/" + inspeccionId + "/envio"));
              return new RespuestaIdempotente(422, json.writeValueAsBytes(rechazo.getBody()));
            }
          }
          return new RespuestaIdempotente(
              200, json.writeValueAsBytes(persistirEnvio(inspeccion, actorId, rol, cuerpo)));
        });
  }

  private InspeccionDTO ejecutarEnvio(
      UUID inspeccionId, UUID actorId, Rol rol, EnviarInspeccionDTO cuerpo) {
    Inspeccion inspeccion = contrato.propio(inspeccionId, actorId);
    if (inspeccion.getEstado() != EstadoInspeccion.ENVIADA) {
      inspeccion.exigirEditable();
      exigirPublicable(inspeccion);
    }
    return persistirEnvio(inspeccion, actorId, rol, cuerpo);
  }

  private InspeccionDTO persistirEnvio(
      Inspeccion inspeccion, UUID actorId, Rol rol, EnviarInspeccionDTO cuerpo) {
    UUID inspeccionId = inspeccion.getId();
    if (inspeccion.getEstado() == EstadoInspeccion.ENVIADA) {
      return consultas.detalle(inspeccionId, actorId, rol);
    }
    Instant dispositivo =
        cuerpo != null && cuerpo.enviadaEn() != null ? cuerpo.enviadaEn() : Instant.now();
    Instant servidor = Instant.now();
    UUID cursoId = inspeccion.getCursoId();
    estados.cambiar(
        inspeccionId, EstadoInspeccion.ENVIADA, actorId, DatosTransicionInspeccion.en(dispositivo));
    inspecciones.marcarSincronizada(inspeccionId, servidor);
    if (rol == Rol.CATEDRATICO || rol == Rol.PROFESIONAL_EXTERNO) {
      return publicacion.publicarDirecta(inspeccionId, actorId);
    }
    avisos.avisar(inspeccionId, cursoId);
    return consultas.detalle(inspeccionId, actorId, rol);
  }

  private void exigirPublicable(Inspeccion inspeccion) {
    List<ErrorCampo> errores = new ArrayList<>();
    recoger(
        () ->
            formularios.validarCompleto(
                inspeccion.getDatos() == null ? Map.of() : inspeccion.getDatos(),
                inspeccion.getFormularioVersionId()),
        errores);
    if (inspeccion.getDatosPermanentesConfirmadosEn() == null) {
      errores.add(
          new ErrorCampo(
              "datos-permanentes-sin-confirmar",
              "Los datos permanentes deben confirmarse antes de enviar."));
    }
    Integer anio =
        referencias
            .puente(inspeccion.getPuenteId())
            .map(ReferenciasInspeccion.PuenteVista::anioConstruccion)
            .orElse(null);
    recoger(() -> fechas.exigir(inspeccion.getFechaInspeccion(), anio), errores);
    long fotos = archivos.contarFotos(inspeccion.getId());
    if (fotos < MIN_FOTOS) {
      errores.add(
          new ErrorCampo(
              "fotos", "Se requieren al menos 4 fotografías vigentes; hay %d.".formatted(fotos)));
    }
    if (errores.isEmpty()) {
      return;
    }
    throw new NegocioException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "inspeccion-no-publicable",
            "La inspección no puede enviarse a revisión",
            "Hay %d problema%s que impide%s el envío."
                .formatted(
                    errores.size(), errores.size() == 1 ? "" : "s", errores.size() == 1 ? "" : "n"))
        .conErrores(List.copyOf(errores));
  }

  private static void recoger(Runnable regla, List<ErrorCampo> destino) {
    try {
      regla.run();
    } catch (NegocioException e) {
      if (e.getStatusCode() != HttpStatus.UNPROCESSABLE_CONTENT) {
        throw e;
      }
      List<ErrorCampo> extras = erroresDe(e);
      if (extras.isEmpty()) {
        destino.add(new ErrorCampo("datos", e.getBody().getDetail()));
      } else {
        destino.addAll(extras);
      }
    }
  }

  private static List<ErrorCampo> erroresDe(NegocioException error) {
    Object crudo =
        error.getBody().getProperties() == null
            ? null
            : error.getBody().getProperties().get("errores");
    if (!(crudo instanceof List<?> lista)) {
      return List.of();
    }
    List<ErrorCampo> campos = new ArrayList<>();
    for (Object item : lista) {
      if (item instanceof ErrorCampo campo) {
        campos.add(campo);
      }
    }
    return campos;
  }
}
