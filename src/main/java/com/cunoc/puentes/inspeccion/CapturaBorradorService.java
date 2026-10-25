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
import com.cunoc.puentes.sync.Idempotencia;
import com.cunoc.puentes.sync.IdempotenciaRepository;
import com.cunoc.puentes.usuario.Rol;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class CapturaBorradorService {

  private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

  private final InspeccionService contrato;
  private final ReferenciasInspeccion referencias;
  private final ConsultaFormulario formularios;
  private final ValidacionFechaInspeccion fechas;
  private final EscrituraBorradorInspeccion escritura;
  private final ConsultaInspeccionService consultas;
  private final AcompananteInspeccionRepository acompanantes;
  private final CopiaConflictoRepository copias;
  private final IdempotenciaRepository idempotencias;
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
      IdempotenciaRepository idempotencias,
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
    this.idempotencias = idempotencias;
    this.em = em;
    this.json = json;
    this.maxBytesAvance = maxBytesAvance;
  }

  @Transactional
  InspeccionDTO guardar(
      UUID inspeccionId,
      UUID actorId,
      Rol rol,
      GuardarAvanceDTO cuerpo,
      String claveIdempotencia,
      boolean sync) {
    if (claveIdempotencia != null && !claveIdempotencia.isBlank()) {
      Optional<InspeccionDTO> previa = respuestaIdempotente(actorId, claveIdempotencia.strip());
      if (previa.isPresent()) {
        return previa.get();
      }
    }
    rechazarInmutables(cuerpo);
    exigirTamano(cuerpo.datos());
    Inspeccion inspeccion = contrato.propioEnBorrador(inspeccionId, actorId);
    if (!sync && !cuerpo.version().equals(inspeccion.getVersion())) {
      throw versionDesactualizada();
    }
    if (sync && !cuerpo.version().equals(inspeccion.getVersion())) {
      copias.save(CopiaConflicto.de(inspeccion.getId(), RutasDatos.copiar(inspeccion.getDatos())));
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
      inspeccion.setFechaInspeccion(cuerpo.fechaInspeccion());
    }
    inspeccion.setDatos(fusion);
    inspeccion.setUltimaActividadEn(Instant.now());
    inspeccion.setAvisoAbandonoEn(null);
    if (sync) {
      inspeccion.setSincronizadaEn(Instant.now());
    }
    escritura.guardar(inspeccion);
    InspeccionDTO dto = consultas.detalle(inspeccion.getId(), actorId, rol);
    guardarIdempotencia(actorId, claveIdempotencia, dto);
    return dto;
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

  @Transactional
  void eliminar(UUID inspeccionId, UUID actorId) {
    Inspeccion inspeccion = contrato.propioEnBorrador(inspeccionId, actorId);
    escritura.eliminar(inspeccion);
  }

  private Optional<InspeccionDTO> respuestaIdempotente(UUID actorId, String clave) {
    return idempotencias
        .findByUsuarioIdAndClave(actorId, clave)
        .map(fila -> json.convertValue(fila.getRespuesta(), InspeccionDTO.class));
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
      // El reintento concurrente ya guardó la respuesta.
    }
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
