package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.archivo.ArchivoService;
import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.dto.EnviarInspeccionDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import com.cunoc.puentes.sync.Idempotencia;
import com.cunoc.puentes.sync.IdempotenciaRepository;
import com.cunoc.puentes.usuario.Rol;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class EnvioInspeccionService {

  static final int MIN_FOTOS = 4;
  private static final Logger LOG = LoggerFactory.getLogger(EnvioInspeccionService.class);
  private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

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
  private final IdempotenciaRepository idempotencias;
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
      IdempotenciaRepository idempotencias,
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
    this.idempotencias = idempotencias;
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
    // X-Sgp-Sync se acepta por el contrato de P6; el envío siempre registra sincronizada_en.
    if (claveIdempotencia != null && !claveIdempotencia.isBlank()) {
      Optional<InspeccionDTO> previa = respuestaIdempotente(actorId, claveIdempotencia.strip());
      if (previa.isPresent()) {
        return previa.get();
      }
    }
    Inspeccion inspeccion = contrato.propio(inspeccionId, actorId);
    if (inspeccion.getEstado() == EstadoInspeccion.ENVIADA) {
      return consultas.detalle(inspeccionId, actorId, rol);
    }
    inspeccion.exigirEditable();
    exigirPublicable(inspeccion);
    Instant dispositivo =
        cuerpo != null && cuerpo.enviadaEn() != null ? cuerpo.enviadaEn() : Instant.now();
    Instant servidor = Instant.now();
    UUID cursoId = inspeccion.getCursoId();
    estados.cambiar(
        inspeccionId, EstadoInspeccion.ENVIADA, actorId, DatosTransicionInspeccion.en(dispositivo));
    inspecciones.marcarSincronizada(inspeccionId, servidor);
    if (rol == Rol.CATEDRATICO || rol == Rol.PROFESIONAL_EXTERNO) {
      InspeccionDTO publicada = publicacion.publicarDirecta(inspeccionId, actorId);
      guardarIdempotencia(actorId, claveIdempotencia, publicada);
      return publicada;
    }
    avisos.avisar(inspeccionId, cursoId);
    InspeccionDTO dto = consultas.detalle(inspeccionId, actorId, rol);
    guardarIdempotencia(actorId, claveIdempotencia, dto);
    return dto;
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

  private Optional<InspeccionDTO> respuestaIdempotente(UUID actorId, String clave) {
    return idempotencias
        .findByUsuarioIdAndClave(actorId, clave)
        .map(fila -> RespuestaIdempotenteInspeccion.leer(fila, json));
  }

  private void guardarIdempotencia(UUID actorId, String clave, InspeccionDTO dto) {
    if (clave == null || clave.isBlank()) {
      return;
    }
    String limpia = clave.strip();
    if (limpia.length() > 100) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "idempotencia-invalida",
          "Clave de idempotencia inválida",
          "Idempotency-Key no puede superar 100 caracteres.");
    }
    try {
      idempotencias.save(
          Idempotencia.de(actorId, limpia, HttpStatus.OK.value(), json.convertValue(dto, MAPA)));
    } catch (DataIntegrityViolationException e) {
      LOG.debug(
          "Idempotencia ya persistida por un reintento concurrente ({})",
          e.getClass().getSimpleName());
    }
  }
}
