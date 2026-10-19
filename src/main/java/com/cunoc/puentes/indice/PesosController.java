package com.cunoc.puentes.indice;

import com.cunoc.puentes.indice.dto.JuegoPesosDTO;
import com.cunoc.puentes.indice.dto.PaginaPesosDTO;
import com.cunoc.puentes.indice.dto.PaginacionPesosDTO;
import com.cunoc.puentes.indice.dto.PesoElementoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMINISTRADOR')")
@Tag(name = "Pesos del IC", description = "Consulta de configuraciones guardadas (RN-IC-03)")
@ApiResponse(responseCode = "401", description = "Sin sesión o con el token vencido")
@ApiResponse(responseCode = "403", description = "Solo Administrador administra pesos")
@ApiResponse(responseCode = "400", description = "UUID o parámetros de página inválidos")
@ApiResponse(responseCode = "404", description = "El formulario o el juego indicado no existen")
class PesosController {

  private final ConsultaPesosService consulta;

  PesosController(ConsultaPesosService consulta) {
    this.consulta = consulta;
  }

  @GetMapping("/formularios/{formularioId}/juegos-pesos")
  @Operation(
      summary = "Lista los juegos de una versión de formulario, por ID descendente",
      description = "page desde 0; size por defecto 20, máximo 100. No selecciona un juego activo.")
  @ApiResponse(responseCode = "200", description = "Página de juegos, vacía si no hay configurados")
  PaginaPesosDTO<JuegoPesosDTO> listar(
      @PathVariable UUID formularioId,
      @Valid @ModelAttribute @ParameterObject PaginacionPesosDTO pagina) {
    return PaginaPesosDTO.de(consulta.listar(formularioId, pagina.page(), pagina.size()));
  }

  @GetMapping("/juegos-pesos/{id}")
  @Operation(summary = "Consulta un juego por su ID; el acta es solo una referencia documental")
  @ApiResponse(responseCode = "200", description = "Metadatos del juego guardado")
  JuegoPesosDTO obtener(@PathVariable UUID id) {
    return consulta.obtener(id);
  }

  @GetMapping("/juegos-pesos/{id}/pesos")
  @Operation(
      summary = "Lista los pesos de un juego por referencia de elemento ascendente",
      description = "page desde 0; size por defecto 20, máximo 100.")
  @ApiResponse(responseCode = "200", description = "Página de elementos y sus pesos")
  PaginaPesosDTO<PesoElementoDTO> listarPesos(
      @PathVariable UUID id, @Valid @ModelAttribute @ParameterObject PaginacionPesosDTO pagina) {
    return PaginaPesosDTO.de(consulta.listarPesos(id, pagina.page(), pagina.size()));
  }
}
