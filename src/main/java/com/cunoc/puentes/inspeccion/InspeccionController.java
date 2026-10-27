package com.cunoc.puentes.inspeccion;

import com.cunoc.puentes.inspeccion.dto.ConfirmarDatosPermanentesDTO;
import com.cunoc.puentes.inspeccion.dto.CrearInspeccionDTO;
import com.cunoc.puentes.inspeccion.dto.DatosPermanentesDTO;
import com.cunoc.puentes.inspeccion.dto.EnviarInspeccionDTO;
import com.cunoc.puentes.inspeccion.dto.GuardarAvanceDTO;
import com.cunoc.puentes.inspeccion.dto.HistorialTransicionDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionDTO;
import com.cunoc.puentes.inspeccion.dto.InspeccionResumenDTO;
import com.cunoc.puentes.inspeccion.dto.PaginaDTO;
import com.cunoc.puentes.inspeccion.dto.PuenteInspeccionableDTO;
import com.cunoc.puentes.inspeccion.dto.ReemplazarAcompanantesDTO;
import com.cunoc.puentes.sync.RespuestaIdempotente;
import com.cunoc.puentes.usuario.Rol;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inspecciones", description = "Consulta, captura y envío de inspecciones (P5)")
@ApiResponse(responseCode = "401", description = "Sin autenticación")
class InspeccionController {

  static final String IDEMPOTENCIA = "Idempotency-Key";
  static final String SYNC = "X-Sgp-Sync";

  private final ConsultaInspeccionService consultas;
  private final InicioInspeccionService inicio;
  private final PrecargaInspeccionService precarga;
  private final CapturaBorradorService captura;
  private final EnvioInspeccionService envio;

  InspeccionController(
      ConsultaInspeccionService consultas,
      InicioInspeccionService inicio,
      PrecargaInspeccionService precarga,
      CapturaBorradorService captura,
      EnvioInspeccionService envio) {
    this.consultas = consultas;
    this.inicio = inicio;
    this.precarga = precarga;
    this.captura = captura;
    this.envio = envio;
  }

  @GetMapping("/puentes-inspeccionables")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
  @Operation(summary = "Puentes que el inspector autenticado puede inspeccionar")
  @ApiResponse(responseCode = "200", description = "Página de puentes inspeccionables")
  @ApiResponse(responseCode = "403", description = "El Administrador no inspecciona")
  PaginaDTO<PuenteInspeccionableDTO> puentesInspeccionables(
      Principal usuario, Authentication autenticacion, Pageable pagina) {
    return PaginaDTO.de(consultas.puentesInspeccionables(id(usuario), rol(autenticacion), pagina));
  }

  @PostMapping("/inspecciones")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
  @Operation(
      summary = "Inicia una inspección o una corrección",
      description =
          "Idempotency-Key opcional: sin clave o en blanco conserva deduplicación UUID y detalle actual. "
              + "Clave no blanca recortada, máximo 100 caracteres Unicode. Huella SHA-256 v1 de POST, ruta "
              + "/api/v1/inspecciones y DTO completo (incluye UUID, puente, asignación, curso, fechas, dispositivo, "
              + "GPS, origen y motivo de corrección); objetos ordenados, números normalizados y UUID canónicos. "
              + "Solo se guarda 201 application/json con status y bytes exactos, sin expiración automática. "
              + "Espera máxima 2 segundos; 409 operacion-en-proceso permite reintentar la misma clave. "
              + "Legado JSONB u otra huella: 409 clave-idempotencia-incompatible sin convertir ni ejecutar. "
              + "Autoriza rol, autor, recurso no eliminado y asignación vigente antes del replay; no exige "
              + "nueva editabilidad, formulario activo ni ausencia de corrección viva para reproducir. "
              + "La baja posterior conserva 409 inspeccion-en-conflicto, no devuelve el resultado antiguo. "
              + "Alta, transición, auditoría y resultado comparten transacción PostgreSQL. "
              + "No se guardan rechazos, binding, permisos ni fallos transitorios. X-Sgp-Sync no aplica al alta.")
  @ApiResponse(
      responseCode = "201",
      description = "Borrador creado, deduplicado por UUID o replay byte-exacto",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = InspeccionDTO.class)))
  @ApiResponse(responseCode = "400", description = "DTO o UUID v7 inválidos")
  @ApiResponse(responseCode = "403", description = "Sin derecho a inspeccionar ese puente")
  @ApiResponse(
      responseCode = "409",
      description =
          "Id ajeno/eliminado, sin formulario, corrección en curso, clave-idempotencia-incompatible u operacion-en-proceso; no se guarda")
  @ApiResponse(
      responseCode = "404",
      description = "Recurso inexistente o identidad del resultado ausente; no se guarda")
  @ApiResponse(responseCode = "500", description = "Fallo transitorio; revierte alta y resultado")
  @ApiResponse(responseCode = "422", description = "Puente inactivo, fecha o corrección inválida")
  ResponseEntity<?> crear(
      Principal usuario,
      Authentication autenticacion,
      @Parameter(
              name = IDEMPOTENCIA,
              in = ParameterIn.HEADER,
              description =
                  "Opcional; se recorta, máximo 100 caracteres Unicode. Misma clave y DTO reproducen el 201 original; sin clave o en blanco conserva el flujo anterior.")
          @RequestHeader(value = IDEMPOTENCIA, required = false)
          String clave,
      @Valid @RequestBody CrearInspeccionDTO datos) {
    if (clave == null || clave.isBlank()) {
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(inicio.iniciar(id(usuario), rol(autenticacion), datos));
    }
    RespuestaIdempotente respuesta =
        inicio.iniciarIdempotente(id(usuario), rol(autenticacion), datos, clave);
    return ResponseEntity.status(respuesta.estadoHttp())
        .contentType(MediaType.APPLICATION_JSON)
        .body(respuesta.cuerpo());
  }

  @GetMapping("/inspecciones")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(summary = "Lista las inspecciones del autor autenticado")
  @ApiResponse(responseCode = "200", description = "Página de inspecciones propias")
  PaginaDTO<InspeccionResumenDTO> listar(
      Principal usuario,
      @RequestParam(name = "puente_id", required = false) UUID puenteId,
      @RequestParam(required = false) EstadoInspeccion estado,
      @RequestParam(name = "fecha_desde", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fechaDesde,
      @RequestParam(name = "fecha_hasta", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fechaHasta,
      Pageable pagina) {
    return PaginaDTO.de(
        consultas.listarMias(id(usuario), puenteId, estado, fechaDesde, fechaHasta, pagina));
  }

  @GetMapping("/inspecciones/{id}")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(summary = "Detalle de una inspección visible para el usuario")
  @ApiResponse(responseCode = "200", description = "DTO de la inspección")
  @ApiResponse(responseCode = "404", description = "Inexistente, eliminada o no visible")
  InspeccionDTO detalle(Principal usuario, Authentication autenticacion, @PathVariable UUID id) {
    return consultas.detalle(id, id(usuario), rol(autenticacion));
  }

  @PutMapping("/inspecciones/{id}")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
  @Operation(
      summary = "Guarda el avance de un borrador propio",
      description =
          "Idempotency-Key opcional. Sin clave o con valor en blanco conserva la escritura previa. "
              + "Una clave no blanca se recorta y admite hasta 100 caracteres Unicode. La huella v1 "
              + "SHA-256 incluye método, ruta con UUID canónico, X-Sgp-Sync y DTO completo; no incluye "
              + "la clave ni la identidad autenticada. Con la misma huella se reproducen status y "
              + "bytes. Se comprueba autenticación, rol y titularidad antes del replay; solo las "
              + "ejecuciones nuevas vuelven a comprobar editabilidad. X-Sgp-Sync=true permite fusionar "
              + "una versión local desactualizada y guardar copia previa. La operación y su resultado "
              + "se confirman en una transacción PostgreSQL. Se guardan éxitos 200 y rechazos terminales "
              + "de payload 413/422; no se guardan errores de autorización, estado/version, "
              + "incompatibilidad de clave ni fallos transitorios. La espera máxima es 2 segundos; "
              + "al agotarse responde 409 operacion-en-proceso, reintentable con la misma clave.")
  @ApiResponse(
      responseCode = "200",
      description = "Avance guardado o replay byte-exacto",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = InspeccionDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "DTO/encabezado inválidos o clave mayor de 100 caracteres (idempotencia-invalida); no se guarda resultado")
  @ApiResponse(responseCode = "401", description = "Sin autenticación; no se guarda resultado")
  @ApiResponse(
      responseCode = "403",
      description = "Rol sin permiso para editar; no se guarda resultado")
  @ApiResponse(
      responseCode = "404",
      description =
          "inspeccion-no-encontrada: inexistente, ajena, eliminada o sin titularidad; no se guarda resultado")
  @ApiResponse(
      responseCode = "409",
      description =
          "version-desactualizada, inspeccion-no-editable, clave-idempotencia-incompatible o "
              + "operacion-en-proceso; no se cachea")
  @ApiResponse(
      responseCode = "413",
      description = "Cuerpo demasiado grande; rechazo terminal de payload reproducible",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description =
          "Rechazo terminal de payload reproducible: campo-inmutable, grupo-con-danos, "
              + "datos-invalidos o fecha-inspeccion-invalida",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "500", description = "Error inesperado; no se guarda resultado")
  ResponseEntity<?> guardar(
      Principal usuario,
      Authentication autenticacion,
      @PathVariable UUID id,
      @Parameter(
              name = IDEMPOTENCIA,
              in = ParameterIn.HEADER,
              description =
                  "Opcional; sin clave o en blanco usa el comportamiento anterior. Una clave no "
                      + "blanca se recorta, máximo 100 caracteres Unicode, y se asocia al usuario "
                      + "autenticado.")
          @RequestHeader(value = IDEMPOTENCIA, required = false)
          String idempotencia,
      @Parameter(
              name = SYNC,
              in = ParameterIn.HEADER,
              description = "true permite fusión sync; forma parte de la huella v1.",
              example = "false")
          @RequestHeader(value = SYNC, defaultValue = "false")
          boolean sync,
      @Valid @RequestBody GuardarAvanceDTO datos) {
    UUID actorId = id(usuario);
    Rol rol = rol(autenticacion);
    if (idempotencia == null || idempotencia.isBlank()) {
      return ResponseEntity.ok(captura.guardar(id, actorId, rol, datos, sync));
    }
    RespuestaIdempotente respuesta =
        captura.guardarIdempotente(id, actorId, rol, datos, idempotencia, sync);
    MediaType tipo =
        respuesta.estadoHttp() == HttpStatus.OK.value()
            ? MediaType.APPLICATION_JSON
            : MediaType.APPLICATION_PROBLEM_JSON;
    return ResponseEntity.status(respuesta.estadoHttp()).contentType(tipo).body(respuesta.cuerpo());
  }

  @DeleteMapping("/inspecciones/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
  @Operation(summary = "Elimina lógicamente un borrador propio")
  @ApiResponse(responseCode = "204", description = "Borrador dado de baja")
  @ApiResponse(responseCode = "404", description = "Inexistente, ajena, eliminada o no visible")
  @ApiResponse(responseCode = "409", description = "No es un borrador")
  void eliminar(Principal usuario, @PathVariable UUID id) {
    captura.eliminar(id, id(usuario));
  }

  @GetMapping("/inspecciones/{id}/historial")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(summary = "Historial de estados de una inspección visible")
  @ApiResponse(responseCode = "200", description = "Transiciones en orden cronológico")
  @ApiResponse(responseCode = "404", description = "Inexistente, eliminada o no visible")
  List<HistorialTransicionDTO> historial(
      Principal usuario, Authentication autenticacion, @PathVariable UUID id) {
    return consultas.historial(id, id(usuario), rol(autenticacion));
  }

  @GetMapping("/inspecciones/{id}/datos-permanentes")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(summary = "Precarga de datos permanentes del borrador")
  @ApiResponse(responseCode = "200", description = "Campos permanentes sin daños")
  @ApiResponse(responseCode = "404", description = "Inexistente, ajena o eliminada")
  DatosPermanentesDTO datosPermanentesDeInspeccion(Principal usuario, @PathVariable UUID id) {
    return precarga.deInspeccion(id, id(usuario));
  }

  @PostMapping("/inspecciones/{id}/confirmacion-datos-permanentes")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
  @Operation(summary = "Confirma o corrige los datos permanentes del borrador")
  @ApiResponse(responseCode = "200", description = "Permanentes guardados")
  @ApiResponse(responseCode = "404", description = "Inexistente, ajena o eliminada")
  @ApiResponse(responseCode = "409", description = "No es un borrador")
  @ApiResponse(responseCode = "422", description = "Daños en el cuerpo o valores inválidos")
  InspeccionDTO confirmarPermanentes(
      Principal usuario,
      Authentication autenticacion,
      @PathVariable UUID id,
      @Valid @RequestBody ConfirmarDatosPermanentesDTO datos) {
    return precarga.confirmar(id, id(usuario), rol(autenticacion), datos);
  }

  @PostMapping("/inspecciones/{id}/envio")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
  @Operation(
      summary = "Envía un borrador propio a revisión",
      description =
          "Idempotency-Key opcional, recortada y de hasta 100 caracteres Unicode. "
              + "Sin clave o en blanco conserva el comportamiento anterior. La huella SHA-256 v1 "
              + "incluye POST, ruta con UUID canónico, X-Sgp-Sync y DTO completo canonicalizado; "
              + "cuerpo ausente y {} son distintos. No incluye fechas generadas por el servidor. "
              + "Autoriza titularidad antes del replay; solo ejecuciones nuevas exigen editabilidad. "
              + "Transición, publicación, avisos persistidos y resultado se confirman en una transacción. "
              + "Reproduce status y bytes exactos del 200 ENVIADA/PUBLICADA o 422 inspeccion-no-publicable. "
              + "Tras corregir datos, permanentes o fotos de un 422 guardado, use una clave nueva. "
              + "Espera 2 segundos; 409 operacion-en-proceso permite reintentar sin cachear el conflicto. "
              + "Legado JSONB u otra huella: 409 clave-idempotencia-incompatible sin conversión ni ejecución. "
              + "Autenticación, permisos, inexistencia, estado, binding y fallos transitorios no se guardan.")
  @ApiResponse(
      responseCode = "200",
      description = "Inspección enviada/publicada o replay byte-exacto",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = InspeccionDTO.class)))
  @ApiResponse(responseCode = "404", description = "Inexistente, ajena o eliminada")
  @ApiResponse(
      responseCode = "400",
      description = "DTO/encabezado o clave inválidos; no se guarda resultado")
  @ApiResponse(responseCode = "403", description = "Sin permiso; no se guarda resultado")
  @ApiResponse(
      responseCode = "409",
      description =
          "inspeccion-no-editable, clave-idempotencia-incompatible u operacion-en-proceso; no se guarda resultado")
  @ApiResponse(
      responseCode = "422",
      description =
          "inspeccion-no-publicable: faltan datos, permanentes, fecha o fotos; reproducible con clave",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  ResponseEntity<?> enviar(
      Principal usuario,
      Authentication autenticacion,
      @PathVariable UUID id,
      @RequestHeader(value = IDEMPOTENCIA, required = false) String idempotencia,
      @RequestHeader(value = SYNC, defaultValue = "false") boolean sync,
      @RequestBody(required = false) @Valid EnviarInspeccionDTO datos) {
    UUID actorId = id(usuario);
    Rol rol = rol(autenticacion);
    if (idempotencia == null || idempotencia.isBlank()) {
      return ResponseEntity.ok(envio.enviar(id, actorId, rol, datos, idempotencia, sync));
    }
    RespuestaIdempotente respuesta =
        envio.enviarIdempotente(id, actorId, rol, datos, idempotencia, sync);
    MediaType tipo =
        respuesta.estadoHttp() == HttpStatus.OK.value()
            ? MediaType.APPLICATION_JSON
            : MediaType.APPLICATION_PROBLEM_JSON;
    return ResponseEntity.status(respuesta.estadoHttp()).contentType(tipo).body(respuesta.cuerpo());
  }

  @PutMapping("/inspecciones/{id}/acompanantes")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
  @Operation(summary = "Reemplaza la lista de acompañantes del borrador")
  @ApiResponse(responseCode = "200", description = "Lista reemplazada")
  @ApiResponse(responseCode = "404", description = "Inexistente, ajena o eliminada")
  @ApiResponse(responseCode = "409", description = "No es un borrador")
  @ApiResponse(responseCode = "422", description = "El autor no puede ser acompañante")
  InspeccionDTO acompanantes(
      Principal usuario,
      Authentication autenticacion,
      @PathVariable UUID id,
      @Valid @RequestBody ReemplazarAcompanantesDTO datos) {
    return captura.reemplazarAcompanantes(id, id(usuario), rol(autenticacion), datos);
  }

  @GetMapping("/puentes/{puenteId}/inspecciones")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO', 'ADMINISTRADOR')")
  @Operation(summary = "Inspecciones publicadas de un puente y los borradores propios")
  @ApiResponse(responseCode = "200", description = "Página ordenada por fecha de inspección")
  @ApiResponse(responseCode = "404", description = "El puente no existe")
  PaginaDTO<InspeccionResumenDTO> delPuente(
      Principal usuario, @PathVariable UUID puenteId, Pageable pagina) {
    return PaginaDTO.de(consultas.listarDelPuente(puenteId, id(usuario), pagina));
  }

  @GetMapping("/puentes/{puenteId}/datos-permanentes")
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
  @Operation(summary = "Precarga del puente para preparar la salida (P6)")
  @ApiResponse(responseCode = "200", description = "Campos permanentes de la última publicada")
  @ApiResponse(responseCode = "403", description = "Sin asignación vigente o rol no inspector")
  @ApiResponse(responseCode = "404", description = "El puente no existe")
  DatosPermanentesDTO datosPermanentesDePuente(
      Principal usuario, Authentication autenticacion, @PathVariable UUID puenteId) {
    return precarga.dePuente(puenteId, id(usuario), rol(autenticacion));
  }

  private static UUID id(Principal usuario) {
    return UUID.fromString(usuario.getName());
  }

  private static Rol rol(Authentication autenticacion) {
    return autenticacion.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(autoridad -> autoridad.startsWith("ROLE_"))
        .map(autoridad -> Rol.valueOf(autoridad.substring(5)))
        .findFirst()
        .orElseThrow();
  }
}
