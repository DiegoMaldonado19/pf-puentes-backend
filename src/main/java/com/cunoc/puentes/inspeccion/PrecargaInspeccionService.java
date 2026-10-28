package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.dto.ConfirmarDatosPermanentesDTO;
import com.cunoc.puentes.inspeccion.dto.DatosPermanentesDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import com.cunoc.puentes.inspeccion.puerto.RutasDatos;
import com.cunoc.puentes.sync.IdempotenciaService;
import com.cunoc.puentes.sync.RespuestaIdempotente;
import com.cunoc.puentes.usuario.Rol;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class PrecargaInspeccionService {

  private final InspeccionRepository inspecciones;
  private final InspeccionService contrato;
  private final ReferenciasInspeccion referencias;
  private final ConsultaFormulario formularios;
  private final EscrituraBorradorInspeccion escritura;
  private final ConsultaInspeccionService consultas;
  private final IdempotenciaService idempotencia;
  private final JsonMapper json;

  PrecargaInspeccionService(
      InspeccionRepository inspecciones,
      InspeccionService contrato,
      ReferenciasInspeccion referencias,
      ConsultaFormulario formularios,
      EscrituraBorradorInspeccion escritura,
      ConsultaInspeccionService consultas,
      IdempotenciaService idempotencia,
      JsonMapper json) {
    this.inspecciones = inspecciones;
    this.contrato = contrato;
    this.referencias = referencias;
    this.formularios = formularios;
    this.escritura = escritura;
    this.consultas = consultas;
    this.idempotencia = idempotencia;
    this.json = json;
  }

  @Transactional(readOnly = true)
  DatosPermanentesDTO deInspeccion(UUID inspeccionId, UUID actorId) {
    Inspeccion inspeccion = contrato.propio(inspeccionId, actorId);
    return armar(inspeccion.getPuenteId(), inspeccion.getFormularioVersionId());
  }

  @Transactional(readOnly = true)
  DatosPermanentesDTO dePuente(UUID puenteId, UUID actorId, Rol rol) {
    ReferenciasInspeccion.PuenteVista puente =
        referencias.puente(puenteId).orElseThrow(PrecargaInspeccionService::puenteNoEncontrado);
    if (!puente.activo()) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "puente-inactivo",
          "El puente está inactivo",
          "Un puente dado de baja no se puede inspeccionar.");
    }
    if (rol == Rol.ESTUDIANTE && referencias.asignacionVigente(actorId, puenteId).isEmpty()) {
      throw new NegocioException(
          HttpStatus.FORBIDDEN,
          "puente-no-asignado",
          "Puente no asignado",
          "No tiene una asignación vigente para inspeccionar ese puente.");
    }
    UUID versionId =
        formularios
            .versionActiva()
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.CONFLICT,
                        "sin-formulario-activo",
                        "No hay formulario activo",
                        "No se puede armar la precarga sin una versión activa del formulario."))
            .id();
    return armar(puenteId, versionId);
  }

  RespuestaIdempotente confirmarIdempotente(
      UUID inspeccionId,
      UUID actorId,
      Rol rol,
      ConfirmarDatosPermanentesDTO cuerpo,
      String encabezado) {
    contrato.propio(inspeccionId, actorId);
    String clave = encabezado.strip();
    if (clave.codePointCount(0, clave.length()) > 100) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "idempotencia-invalida",
          "Clave de idempotencia inválida",
          "Idempotency-Key no puede superar 100 caracteres.");
    }
    RespuestaIdempotente respuesta =
        idempotencia.ejecutar(
            actorId,
            clave,
            HuellaPermanentesIdempotencia.crear(inspeccionId, cuerpo, json),
            Duration.ofSeconds(2),
            () ->
                new RespuestaIdempotente(
                    HttpStatus.OK.value(),
                    json.writeValueAsBytes(confirmar(inspeccionId, actorId, rol, cuerpo))));
    // La espera por otra petición no debe conservar un permiso revocado entre tanto.
    contrato.propio(inspeccionId, actorId);
    return respuesta;
  }

  @Transactional
  InspeccionDTO confirmar(
      UUID inspeccionId, UUID actorId, Rol rol, ConfirmarDatosPermanentesDTO cuerpo) {
    Inspeccion inspeccion = contrato.propioEnBorrador(inspeccionId, actorId);
    Map<String, Object> enviados = cuerpo.datos() == null ? Map.of() : cuerpo.datos();
    Set<String> danos = formularios.rutasDeDano(inspeccion.getFormularioVersionId());
    List<ErrorCampo> dañosEnviados = new ArrayList<>();
    for (String ruta : danos) {
      if (RutasDatos.leer(enviados, ruta) != null) {
        dañosEnviados.add(new ErrorCampo(ruta, "Los daños no se confirman por esta vía."));
      }
    }
    if (!dañosEnviados.isEmpty()) {
      throw new NegocioException(
              HttpStatus.UNPROCESSABLE_CONTENT,
              "datos-permanentes-invalidos",
              "La confirmación incluye daños",
              "Solo se confirman campos permanentes.")
          .conErrores(dañosEnviados);
    }
    formularios.validarParcial(enviados, inspeccion.getFormularioVersionId());
    Set<String> permanentes = formularios.rutasPermanentes(inspeccion.getFormularioVersionId());
    Map<String, Object> actual = RutasDatos.copiar(inspeccion.getDatos());
    for (String ruta : permanentes) {
      Object valor = RutasDatos.leer(enviados, ruta);
      if (valor != null) {
        RutasDatos.escribir(actual, ruta, valor);
      }
    }
    inspeccion.setDatos(
        RutasDatos.fusionar(actual, referencias.identificacion(inspeccion.getPuenteId())));
    inspeccion.setDatosPermanentesConfirmadosEn(Instant.now());
    inspeccion.setPrecargadaDeId(
        ultimaPublicada(inspeccion.getPuenteId()).map(Inspeccion::getId).orElse(null));
    inspeccion.setUltimaActividadEn(Instant.now());
    inspeccion.setAvisoAbandonoEn(null);
    escritura.guardar(inspeccion);
    return consultas.detalle(inspeccion.getId(), actorId, rol);
  }

  private DatosPermanentesDTO armar(UUID puenteId, UUID versionId) {
    ReferenciasInspeccion.PuenteVista puente = referencias.puente(puenteId).orElse(null);
    Integer anio = puente == null ? null : puente.anioConstruccion();
    Map<String, Object> identificacion =
        puente == null ? Map.of() : referencias.identificacion(puenteId);
    Inspeccion origen = ultimaPublicada(puenteId).orElse(null);
    if (origen == null) {
      return new DatosPermanentesDTO(identificacion, null, anio);
    }
    Map<String, Object> datosOrigen =
        formularios.traducir(origen.getDatos(), origen.getFormularioVersionId(), versionId);
    Map<String, Object> permanentes =
        RutasDatos.extraer(datosOrigen, formularios.rutasPermanentes(versionId));
    return new DatosPermanentesDTO(
        RutasDatos.fusionar(permanentes, identificacion), origen.getId(), anio);
  }

  private java.util.Optional<Inspeccion> ultimaPublicada(UUID puenteId) {
    return inspecciones
        .findFirstByPuenteIdAndEstadoAndEliminadoEnIsNullOrderByFechaInspeccionDescPublicadaEnDesc(
            puenteId, EstadoInspeccion.PUBLICADA);
  }

  private static NegocioException puenteNoEncontrado() {
    return new NegocioException(
        HttpStatus.NOT_FOUND,
        "puente-no-encontrado",
        "Puente no encontrado",
        "No existe un puente con el id indicado.");
  }
}
