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
import com.cunoc.puentes.usuario.Rol;
import io.swagger.v3.oas.annotations.Operation;
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
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
  @Operation(summary = "Inicia una inspección o una corrección")
  @ApiResponse(responseCode = "201", description = "Borrador creado o reenviado (idempotente)")
  @ApiResponse(responseCode = "400", description = "DTO o UUID v7 inválidos")
  @ApiResponse(responseCode = "403", description = "Sin derecho a inspeccionar ese puente")
  @ApiResponse(responseCode = "409", description = "Id ajeno, sin formulario o corrección en curso")
  @ApiResponse(responseCode = "422", description = "Puente inactivo, fecha o corrección inválida")
  InspeccionDTO crear(
      Principal usuario,
      Authentication autenticacion,
      @Valid @RequestBody CrearInspeccionDTO datos) {
    return inicio.iniciar(id(usuario), rol(autenticacion), datos);
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
  @Operation(summary = "Guarda el avance de un borrador propio")
  @ApiResponse(responseCode = "200", description = "Avance guardado")
  @ApiResponse(responseCode = "404", description = "Inexistente, ajena o eliminada")
  @ApiResponse(responseCode = "409", description = "No es borrador o la versión está vieja")
  @ApiResponse(responseCode = "413", description = "Cuerpo demasiado grande")
  @ApiResponse(responseCode = "422", description = "Datos, fecha o campos inmutables inválidos")
  InspeccionDTO guardar(
      Principal usuario,
      Authentication autenticacion,
      @PathVariable UUID id,
      @RequestHeader(value = IDEMPOTENCIA, required = false) String idempotencia,
      @RequestHeader(value = SYNC, defaultValue = "false") boolean sync,
      @Valid @RequestBody GuardarAvanceDTO datos) {
    return captura.guardar(id, id(usuario), rol(autenticacion), datos, idempotencia, sync);
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
  @Operation(summary = "Envía un borrador propio a revisión")
  @ApiResponse(responseCode = "200", description = "Inspección enviada o reenviada (idempotente)")
  @ApiResponse(responseCode = "404", description = "Inexistente, ajena o eliminada")
  @ApiResponse(responseCode = "409", description = "No es un borrador")
  @ApiResponse(responseCode = "422", description = "Faltan datos, permanentes, fecha o fotos")
  InspeccionDTO enviar(
      Principal usuario,
      Authentication autenticacion,
      @PathVariable UUID id,
      @RequestHeader(value = IDEMPOTENCIA, required = false) String idempotencia,
      @RequestHeader(value = SYNC, defaultValue = "false") boolean sync,
      @RequestBody(required = false) @Valid EnviarInspeccionDTO datos) {
    return envio.enviar(id, id(usuario), rol(autenticacion), datos, idempotencia, sync);
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
