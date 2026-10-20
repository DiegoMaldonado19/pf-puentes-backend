package com.cunoc.puentes.puente;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.puente.dto.CrearPuenteDTO;
import com.cunoc.puentes.puente.dto.EditarPuenteDTO;
import com.cunoc.puentes.puente.dto.PaginaPuentesDTO;
import com.cunoc.puentes.puente.dto.PuenteCercanoDTO;
import com.cunoc.puentes.puente.dto.PuenteDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lectura pública de puentes activos; alta y edición solo del Administrador (RN-INV-07). Las rutas
 * públicas son GET /api/v1/puentes y GET /api/v1/puentes/*, nunca /**, para no abrir las rutas
 * anidadas de otros módulos.
 */
@RestController
@RequestMapping("/api/v1/puentes")
@Tag(name = "Puentes", description = "Inventario de puentes y mapa público")
class PuenteController {

  private static final String ADMINISTRADOR = "ROLE_ADMINISTRADOR";

  private final PuenteService servicio;

  PuenteController(PuenteService servicio) {
    this.servicio = servicio;
  }

  @Operation(
      summary = "Lista paginada de puentes",
      description =
          "Público; también alimenta el mapa (DT-BE-08: paginado, máximo 100). Filtros"
              + " combinables; solo activos salvo incluir_bajas, que es del Administrador.")
  @ApiResponse(
      responseCode = "200",
      description = "Página de puentes ordenada por código",
      content = @Content(schema = @Schema(implementation = PaginaPuentesDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Paginación, estado o búsqueda inválidos",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "incluir_bajas sin ser Administrador",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping
  @PreAuthorize("permitAll()")
  PaginaPuentesDTO listar(
      @Parameter(description = "Código INE del departamento", example = "09")
          @RequestParam(required = false)
          String departamento,
      @Parameter(description = "Código INE del municipio", example = "0901")
          @RequestParam(required = false)
          String municipio,
      @RequestParam(required = false) EstadoFiltro estado,
      @Parameter(description = "Texto en nombre, código o ruta (máximo 120)")
          @RequestParam(required = false)
          String q,
      @RequestParam(name = "incluir_bajas", defaultValue = "false") boolean incluirBajas,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication usuario) {
    if (incluirBajas && !esAdministrador(usuario)) {
      throw new NegocioException(
          HttpStatus.FORBIDDEN,
          "acceso-denegado",
          "Acceso denegado",
          "Solo el Administrador puede ver los puentes dados de baja.");
    }
    return servicio.listar(departamento, municipio, estado, q, incluirBajas, page, size);
  }

  @Operation(
      summary = "Puentes activos a menos de 100 m de un punto",
      description = "Para advertir duplicados en los formularios antes de enviar (RN-INV-06).")
  @ApiResponse(
      responseCode = "200",
      description = "Posibles duplicados, del más cercano al más lejano",
      content =
          @Content(array = @ArraySchema(schema = @Schema(implementation = PuenteCercanoDTO.class))))
  @ApiResponse(
      responseCode = "401",
      description = "Sin sesión",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "Rol sin permiso",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/cercanos")
  @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CATEDRATICO')")
  List<PuenteCercanoDTO> cercanos(
      @RequestParam double latitud,
      @RequestParam double longitud,
      @Parameter(description = "Puente que se edita, para no detectarse a sí mismo")
          @RequestParam(required = false)
          UUID excluir) {
    return servicio.cercanos(latitud, longitud, excluir);
  }

  @Operation(summary = "Ficha de un puente", description = "Coordenadas en WGS84 y UTM.")
  @ApiResponse(
      responseCode = "200",
      description = "Ficha del puente",
      content = @Content(schema = @Schema(implementation = PuenteDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Id con formato inválido",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No existe o está dado de baja (salvo para el Administrador)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{id}")
  @PreAuthorize("permitAll()")
  PuenteDTO detalle(@PathVariable UUID id, Authentication usuario) {
    return servicio.detalle(id, esAdministrador(usuario));
  }

  @Operation(
      summary = "Registra un puente",
      description =
          "El sistema asigna el código GT-DD-MMMM-NNNN. Si hay puentes a menos de 100 m responde"
              + " 409 con la lista; reenviar con confirmar_duplicado=true para continuar.")
  @ApiResponse(
      responseCode = "201",
      description = "Puente creado",
      content = @Content(schema = @Schema(implementation = PuenteDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Campos obligatorios faltantes o fuera de rango",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Sin sesión",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "No es Administrador",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Posible duplicado (propiedad cercanos) o correlativo agotado",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Municipio inexistente o que no pertenece al departamento",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  ResponseEntity<PuenteDTO> crear(@Valid @RequestBody CrearPuenteDTO datos) {
    PuenteDTO creado = servicio.crear(datos);
    return ResponseEntity.created(URI.create("/api/v1/puentes/" + creado.id())).body(creado);
  }

  @Operation(
      summary = "Edita los datos descriptivos de un puente",
      description =
          "No cambia código, municipio ni los campos que salen de la inspección (RN-INV-02,"
              + " RN-INV-09).")
  @ApiResponse(
      responseCode = "200",
      description = "Puente actualizado",
      content = @Content(schema = @Schema(implementation = PuenteDTO.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Campos obligatorios faltantes o fuera de rango",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "Sin sesión",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "403",
      description = "No es Administrador",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "El puente no existe",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Puente dado de baja o posible duplicado",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('ADMINISTRADOR')")
  PuenteDTO editar(@PathVariable UUID id, @Valid @RequestBody EditarPuenteDTO datos) {
    return servicio.editar(id, datos);
  }

  private static boolean esAdministrador(Authentication usuario) {
    return usuario != null
        && usuario.getAuthorities().stream().anyMatch(a -> ADMINISTRADOR.equals(a.getAuthority()));
  }
}
