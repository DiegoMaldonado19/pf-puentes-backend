package com.cunoc.puentes.puente;

import com.cunoc.puentes.puente.dto.DepartamentoDTO;
import com.cunoc.puentes.puente.dto.MunicipioDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Público: lo usan el mapa, los filtros y los formularios de alta (RN-INV-04). */
@RestController
@RequestMapping("/api/v1/departamentos")
@Tag(name = "Catálogo INE", description = "Departamentos y municipios de Guatemala")
class CatalogoIneController {

  private final CatalogoIneService servicio;

  CatalogoIneController(CatalogoIneService servicio) {
    this.servicio = servicio;
  }

  @Operation(summary = "Lista los 22 departamentos, ordenados por nombre")
  @ApiResponse(
      responseCode = "200",
      description = "Departamentos",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              array = @ArraySchema(schema = @Schema(implementation = DepartamentoDTO.class))))
  @GetMapping
  @PreAuthorize("permitAll()")
  List<DepartamentoDTO> listarDepartamentos() {
    return servicio.listarDepartamentos();
  }

  @Operation(summary = "Lista los municipios de un departamento, ordenados por nombre")
  @ApiResponse(
      responseCode = "200",
      description = "Municipios del departamento",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              array = @ArraySchema(schema = @Schema(implementation = MunicipioDTO.class))))
  @ApiResponse(
      responseCode = "404",
      description = "El departamento no existe",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @GetMapping("/{codigo}/municipios")
  @PreAuthorize("permitAll()")
  List<MunicipioDTO> listarMunicipios(@PathVariable String codigo) {
    return servicio.listarMunicipios(codigo);
  }
}
