package com.cunoc.puentes.formulario;

import com.cunoc.puentes.formulario.dto.CrearFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.EditarFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.FormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PaginaFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PublicarFormularioVersionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/formularios")
@Tag(name = "Formularios", description = "Motor de formularios de evaluación y versionado (RN-FRM)")
public class FormularioController {

  private final FormularioVersionService servicio;

  public FormularioController(FormularioVersionService servicio) {
    this.servicio = servicio;
  }

  @GetMapping("/activo")
  @PreAuthorize("isAuthenticated()")
  @ApiResponse(responseCode = "200", description = "Versión activa")
  @ApiResponse(responseCode = "404", description = "No hay versión activa")
  @ApiResponse(responseCode = "401", description = "Sin sesión")
  @Operation(
      summary = "Obtiene la versión activa del formulario para nuevas inspecciones y caché offline")
  public ResponseEntity<FormularioVersionDTO> obtenerActivo() {
    return ResponseEntity.ok(servicio.obtenerActivo());
  }

  @GetMapping("/versiones/{codigo}")
  @PreAuthorize("isAuthenticated()")
  @ApiResponse(responseCode = "200", description = "Versión por código")
  @ApiResponse(responseCode = "404", description = "Código inexistente")
  @ApiResponse(responseCode = "401", description = "Sin sesión")
  @Operation(summary = "Obtiene una versión de formulario por su código para consulta histórica")
  public ResponseEntity<FormularioVersionDTO> obtenerPorCodigo(@PathVariable String codigo) {
    return ResponseEntity.ok(servicio.obtenerPorCodigo(codigo));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @ApiResponse(responseCode = "200", description = "Detalle de la versión")
  @ApiResponse(responseCode = "404", description = "Versión inexistente")
  @ApiResponse(responseCode = "401", description = "Sin sesión")
  @Operation(summary = "Obtiene el detalle de una versión de formulario por su identificador")
  public ResponseEntity<FormularioVersionDTO> detalle(@PathVariable UUID id) {
    return ResponseEntity.ok(servicio.detalle(id));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  @ApiResponse(responseCode = "200", description = "Página de versiones")
  @ApiResponse(responseCode = "403", description = "Su rol no lista formularios")
  @ApiResponse(responseCode = "401", description = "Sin sesión")
  @Operation(summary = "Lista paginada de versiones de formularios registradas")
  public ResponseEntity<PaginaFormularioVersionDTO> listar(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
    return ResponseEntity.ok(servicio.listar(pageable));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Operation(summary = "Crea un nuevo borrador de versión de formulario")
  @ApiResponse(responseCode = "201", description = "Borrador creado")
  @ApiResponse(responseCode = "409", description = "Código duplicado")
  @ApiResponse(responseCode = "422", description = "El esquema no es un JSON Schema válido")
  @ApiResponse(responseCode = "403", description = "Solo administrador")
  @ApiResponse(responseCode = "401", description = "Sin sesión")
  public ResponseEntity<FormularioVersionDTO> crearBorrador(
      @Valid @RequestBody CrearFormularioVersionDTO datos) {
    FormularioVersionDTO creado = servicio.crearBorrador(datos);
    return ResponseEntity.created(URI.create("/api/v1/formularios/" + creado.id())).body(creado);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @ApiResponse(responseCode = "200", description = "Borrador modificado")
  @ApiResponse(responseCode = "409", description = "La versión ya no es borrador")
  @ApiResponse(responseCode = "404", description = "Versión inexistente")
  @ApiResponse(responseCode = "422", description = "El esquema no es un JSON Schema válido")
  @ApiResponse(responseCode = "403", description = "Solo administrador")
  @ApiResponse(responseCode = "401", description = "Sin sesión")
  @Operation(
      summary = "Modifica el esquema de un borrador de formulario (solo si no está publicado)")
  public ResponseEntity<FormularioVersionDTO> editarBorrador(
      @PathVariable UUID id, @Valid @RequestBody EditarFormularioVersionDTO datos) {
    return ResponseEntity.ok(servicio.editarBorrador(id, datos));
  }

  @PostMapping("/{id}/publicar")
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @ApiResponse(responseCode = "200", description = "Versión publicada y activa")
  @ApiResponse(responseCode = "409", description = "Solo se publican borradores")
  @ApiResponse(responseCode = "404", description = "Versión inexistente")
  @ApiResponse(responseCode = "403", description = "Solo administrador")
  @ApiResponse(responseCode = "401", description = "Sin sesión")
  @Operation(
      summary = "Publica la versión haciéndola ACTIVA, retirando la previa y registrando el mapeo")
  public ResponseEntity<FormularioVersionDTO> publicar(
      @PathVariable UUID id, @RequestBody(required = false) PublicarFormularioVersionDTO datos) {
    return ResponseEntity.ok(servicio.publicar(id, datos));
  }
}
