package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.dto.AcompananteDTO;
import com.cunoc.puentes.inspeccion.dto.GuardarAvanceDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.inspeccion.dto.ReemplazarAcompanantesDTO;
import com.cunoc.puentes.inspeccion.puerto.ConsultaFormulario;
import com.cunoc.puentes.inspeccion.puerto.GrupoElemento;
import com.cunoc.puentes.inspeccion.puerto.RutasDatos;
import com.cunoc.puentes.sync.CopiaConflicto;
import com.cunoc.puentes.sync.CopiaConflictoRepository;
import com.cunoc.puentes.sync.HuellaIdempotencia;
import com.cunoc.puentes.sync.IdempotenciaService;
import com.cunoc.puentes.sync.RespuestaIdempotente;
import com.cunoc.puentes.usuario.Rol;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class CapturaBorradorService {

  private static final Duration ESPERA_IDEMPOTENCIA = Duration.ofSeconds(2);
  private static final Map<String, Integer> RECHAZOS_TERMINALES =
      Map.of(
          "campo-inmutable", HttpStatus.UNPROCESSABLE_CONTENT.value(),
          "cuerpo-demasiado-grande", HttpStatus.CONTENT_TOO_LARGE.value(),
          "grupo-con-danos", HttpStatus.UNPROCESSABLE_CONTENT.value(),
          "datos-invalidos", HttpStatus.UNPROCESSABLE_CONTENT.value(),
          "fecha-inspeccion-invalida", HttpStatus.UNPROCESSABLE_CONTENT.value());

  private final InspeccionService contrato;
  private final ReferenciasInspeccion referencias;
  private final ConsultaFormulario formularios;
  private final ValidacionFechaInspeccion fechas;
  private final EscrituraBorradorInspeccion escritura;
  private final ConsultaInspeccionService consultas;
  private final AcompananteInspeccionRepository acompanantes;
  private final CopiaConflictoRepository copias;
  private final IdempotenciaService idempotencia;
  private final EntityManager em;
  private final JsonMapper json;
  private final int maxBytesAvance;

  CapturaBorradorService(
      InspeccionService contrato,
      ReferenciasInspeccion referencias,
      ConsultaFormulario formularios,
      ValidacionFechaInspeccion fechas,
      EscrituraBorradorInspeccion escritura,
      ConsultaInspeccionService consultas,
      AcompananteInspeccionRepository acompanantes,
      CopiaConflictoRepository copias,
      IdempotenciaService idempotencia,
      EntityManager em,
      JsonMapper json,
      @Value("${sgp.inspeccion.max-bytes-avance:262144}") int maxBytesAvance) {
    this.contrato = contrato;
    this.referencias = referencias;
    this.formularios = formularios;
    this.fechas = fechas;
    this.escritura = escritura;
    this.consultas = consultas;
    this.acompanantes = acompanantes;
    this.copias = copias;
    this.idempotencia = idempotencia;
    this.em = em;
    this.json = json;
    this.maxBytesAvance = maxBytesAvance;
  }

  @Transactional
  InspeccionDTO guardar(
      UUID inspeccionId, UUID actorId, Rol rol, GuardarAvanceDTO cuerpo, boolean sync) {
    return persistir(validar(inspeccionId, actorId, cuerpo, sync, false), actorId, rol);
  }

  RespuestaIdempotente guardarIdempotente(
      UUID inspeccionId,
      UUID actorId,
      Rol rol,
      GuardarAvanceDTO cuerpo,
      String claveIdempotencia,
      boolean sync) {
    // La propiedad y existencia se comprueban incluso al reproducir una respuesta guardada.
    contrato.propio(inspeccionId, actorId);
    String clave = claveIdempotencia.strip();
    if (clave.codePointCount(0, clave.length()) > 100) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "idempotencia-invalida",
          "Clave de idempotencia inválida",
          "Idempotency-Key no puede superar 100 caracteres.");
    }
    HuellaIdempotencia huella = HuellaBorradorIdempotencia.crear(inspeccionId, sync, cuerpo, json);
    return idempotencia.ejecutar(
        actorId,
        clave,
        huella,
        ESPERA_IDEMPOTENCIA,
        () -> ejecutarIdempotente(inspeccionId, actorId, rol, cuerpo, sync));
  }

  private RespuestaIdempotente ejecutarIdempotente(
      UUID inspeccionId, UUID actorId, Rol rol, GuardarAvanceDTO cuerpo, boolean sync) {
    AvanceValidado avance;
    try {
      avance = validar(inspeccionId, actorId, cuerpo, sync, true);
    } catch (NegocioException rechazo) {
      if (!esRechazoTerminal(rechazo)) {
        throw rechazo;
      }
      rechazo.getBody().setInstance(URI.create("/api/v1/inspecciones/" + inspeccionId));
      return new RespuestaIdempotente(
          rechazo.getStatusCode().value(), json.writeValueAsBytes(rechazo.getBody()));
    }
    InspeccionDTO dto = persistir(avance, actorId, rol);
    return new RespuestaIdempotente(HttpStatus.OK.value(), json.writeValueAsBytes(dto));
  }

  private AvanceValidado validar(
      UUID inspeccionId,
      UUID actorId,
      GuardarAvanceDTO cuerpo,
      boolean sync,
      boolean autorizarAntesDelPayload) {
    Inspeccion inspeccion;
    if (autorizarAntesDelPayload) {
      inspeccion = contrato.propioEnBorrador(inspeccionId, actorId);
      rechazarInmutables(cuerpo);
      exigirTamano(cuerpo.datos());
    } else {
      rechazarInmutables(cuerpo);
      exigirTamano(cuerpo.datos());
      inspeccion = contrato.propioEnBorrador(inspeccionId, actorId);
    }

    boolean versionDesactualizada = !cuerpo.version().equals(inspeccion.getVersion());
    if (!sync && versionDesactualizada) {
      throw versionDesactualizada();
    }
    Map<String, Object> fusion = RutasDatos.fusionar(inspeccion.getDatos(), cuerpo.datos());
    aplicarSinDanos(fusion, cuerpo.gruposSinDanos(), inspeccion.getFormularioVersionId());
    formularios.validarParcial(fusion, inspeccion.getFormularioVersionId());
    if (cuerpo.fechaInspeccion() != null) {
      Integer anio =
          referencias
              .puente(inspeccion.getPuenteId())
              .map(ReferenciasInspeccion.PuenteVista::anioConstruccion)
              .orElse(null);
      fechas.exigir(cuerpo.fechaInspeccion(), anio);
    }
    return new AvanceValidado(
        inspeccion, fusion, cuerpo.fechaInspeccion(), sync && versionDesactualizada, sync);
  }

  private InspeccionDTO persistir(AvanceValidado avance, UUID actorId, Rol rol) {
    Inspeccion inspeccion = avance.inspeccion();
    if (avance.guardarCopiaConflicto()) {
      copias.save(CopiaConflicto.de(inspeccion.getId(), RutasDatos.copiar(inspeccion.getDatos())));
    }
    if (avance.fechaInspeccion() != null) {
      inspeccion.setFechaInspeccion(avance.fechaInspeccion());
    }
    inspeccion.setDatos(avance.datos());
    inspeccion.setUltimaActividadEn(Instant.now());
    inspeccion.setAvisoAbandonoEn(null);
    if (avance.sync()) {
      inspeccion.setSincronizadaEn(Instant.now());
    }
    escritura.guardar(inspeccion);
    return consultas.detalle(inspeccion.getId(), actorId, rol);
  }

  private static boolean esRechazoTerminal(NegocioException rechazo) {
    URI tipo = rechazo.getBody().getType();
    if (tipo == null) {
      return false;
    }
    String ruta = tipo.getPath();
    String codigo = ruta.substring(ruta.lastIndexOf('/') + 1);
    return Integer.valueOf(rechazo.getStatusCode().value()).equals(RECHAZOS_TERMINALES.get(codigo));
  }

  private record AvanceValidado(
      Inspeccion inspeccion,
      Map<String, Object> datos,
      java.time.LocalDate fechaInspeccion,
      boolean guardarCopiaConflicto,
      boolean sync) {}

  RespuestaIdempotente reemplazarAcompanantesIdempotente(
      UUID inspeccionId,
      UUID actorId,
      Rol rol,
      ReemplazarAcompanantesDTO cuerpo,
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
            HuellaAcompanantesIdempotencia.crear(inspeccionId, cuerpo, json),
            Duration.ofSeconds(2),
            () ->
                new RespuestaIdempotente(
                    HttpStatus.OK.value(),
                    json.writeValueAsBytes(
                        reemplazarAcompanantes(inspeccionId, actorId, rol, cuerpo))));
    contrato.propio(inspeccionId, actorId);
    return respuesta;
  }

  @Transactional
  InspeccionDTO reemplazarAcompanantes(
      UUID inspeccionId, UUID actorId, Rol rol, ReemplazarAcompanantesDTO cuerpo) {
    Inspeccion inspeccion = contrato.propioEnBorrador(inspeccionId, actorId);
    for (AcompananteDTO fila : cuerpo.acompanantes()) {
      if (actorId.equals(fila.usuarioId())) {
        throw new NegocioException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "acompanante-invalido",
                "El autor no es acompañante",
                "El responsable del registro no puede figurar como acompañante.")
            .conErrores(
                List.of(
                    new ErrorCampo("usuario_id", "El autor no puede ser su propio acompañante.")));
      }
    }
    acompanantes.deleteByInspeccion_Id(inspeccionId);
    em.flush();
    for (AcompananteDTO fila : cuerpo.acompanantes()) {
      AcompananteInspeccion nuevo = new AcompananteInspeccion();
      nuevo.setInspeccion(inspeccion);
      nuevo.setNombre(fila.nombre().strip());
      nuevo.setUsuarioId(fila.usuarioId());
      acompanantes.save(nuevo);
    }
    inspeccion.setUltimaActividadEn(Instant.now());
    inspeccion.setAvisoAbandonoEn(null);
    escritura.guardar(inspeccion);
    return consultas.detalle(inspeccion.getId(), actorId, rol);
  }

  RespuestaIdempotente eliminarIdempotente(UUID inspeccionId, UUID actorId, String encabezado) {
    contrato.propioParaReplayDeBaja(inspeccionId, actorId);
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
            HuellaBajaBorradorIdempotencia.crear(inspeccionId, json),
            ESPERA_IDEMPOTENCIA,
            () -> {
              // La misma baja auditada participa en la transacción del núcleo. Revalida
              // propiedad, existencia y editabilidad después de esperar por la clave.
              eliminar(inspeccionId, actorId);
              return new RespuestaIdempotente(HttpStatus.NO_CONTENT.value(), new byte[0]);
            });
    contrato.propioParaReplayDeBaja(inspeccionId, actorId);
    return respuesta;
  }

  @Transactional
  void eliminar(UUID inspeccionId, UUID actorId) {
    Inspeccion inspeccion = contrato.propioEnBorrador(inspeccionId, actorId);
    escritura.eliminar(inspeccion);
  }

  private void aplicarSinDanos(Map<String, Object> datos, List<String> grupos, UUID versionId) {
    if (grupos == null || grupos.isEmpty()) {
      return;
    }
    Map<String, GrupoElemento> porId = new LinkedHashMap<>();
    for (GrupoElemento grupo : formularios.grupos(versionId)) {
      porId.put(grupo.id(), grupo);
    }
    List<ErrorCampo> errores = new ArrayList<>();
    for (String id : grupos) {
      GrupoElemento grupo = porId.get(id);
      if (grupo == null) {
        errores.add(new ErrorCampo(id, "No hay un grupo de daño con ese identificador."));
        continue;
      }
      for (String ruta : grupo.rutasDano()) {
        Object valor = RutasDatos.leer(datos, ruta);
        if (valor == null) {
          RutasDatos.escribir(datos, ruta, 0);
          continue;
        }
        if (!esCero(valor)) {
          errores.add(
              new ErrorCampo(
                  id, "Un grupo marcado sin daños no puede tener calificaciones distintas de 0."));
          break;
        }
      }
    }
    if (!errores.isEmpty()) {
      throw new NegocioException(
              HttpStatus.UNPROCESSABLE_CONTENT,
              "grupo-con-danos",
              "El grupo no está sano",
              "Un grupo marcado sin daños exige todas sus calificaciones en 0.")
          .conErrores(errores);
    }
  }

  private void exigirTamano(Map<String, Object> datos) {
    int bytes = json.writeValueAsBytes(datos == null ? Map.of() : datos).length;
    if (bytes > maxBytesAvance) {
      throw new NegocioException(
          HttpStatus.CONTENT_TOO_LARGE,
          "cuerpo-demasiado-grande",
          "El avance es demasiado grande",
          "El cuerpo supera el tamaño máximo permitido.");
    }
  }

  private static void rechazarInmutables(GuardarAvanceDTO cuerpo) {
    List<ErrorCampo> errores = new ArrayList<>();
    if (cuerpo.puenteId() != null) {
      errores.add(new ErrorCampo("puente_id", "El puente no se puede cambiar."));
    }
    if (cuerpo.autorId() != null) {
      errores.add(new ErrorCampo("autor_id", "El autor no se puede cambiar."));
    }
    if (cuerpo.formularioVersionId() != null) {
      errores.add(
          new ErrorCampo("formulario_version_id", "La versión del formulario está congelada."));
    }
    if (cuerpo.estado() != null) {
      errores.add(new ErrorCampo("estado", "El estado no se cambia por esta vía."));
    }
    if (cuerpo.indiceCondicion() != null) {
      errores.add(new ErrorCampo("indice_condicion", "El índice de condición no se edita a mano."));
    }
    if (!errores.isEmpty()) {
      throw new NegocioException(
              HttpStatus.UNPROCESSABLE_CONTENT,
              "campo-inmutable",
              "Hay campos que no se pueden cambiar",
              "El puente, el autor, la versión del formulario, el estado y el IC son inmutables.")
          .conErrores(errores);
    }
  }

  private static boolean esCero(Object valor) {
    return valor instanceof Number numero && numero.doubleValue() == 0d;
  }

  private static NegocioException versionDesactualizada() {
    return new NegocioException(
        HttpStatus.CONFLICT,
        "version-desactualizada",
        "La versión está desactualizada",
        "El servidor ya tiene una versión más nueva que la del cliente.");
  }
}
