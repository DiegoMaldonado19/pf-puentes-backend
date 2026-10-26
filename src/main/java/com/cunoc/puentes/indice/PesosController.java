package com.cunoc.puentes.indice;

import com.cunoc.puentes.indice.dto.CrearJuegoPesosDTO;
import com.cunoc.puentes.indice.dto.FormularioConPesosDTO;
import com.cunoc.puentes.indice.dto.JuegoPesosDTO;
import com.cunoc.puentes.indice.dto.PaginaPesosDTO;
import com.cunoc.puentes.indice.dto.PaginacionPesosDTO;
import com.cunoc.puentes.indice.dto.PesoElementoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final ConsultaElementosDanoService elementos;
  private final CreacionJuegoPesosService creacion;

  PesosController(
      ConsultaPesosService consulta,
      ConsultaElementosDanoService elementos,
      CreacionJuegoPesosService creacion) {
    this.consulta = consulta;
    this.elementos = elementos;
    this.creacion = creacion;
  }

  @PostMapping("/formularios/{formularioId}/juegos-pesos")
  @Operation(
      summary = "Crea un juego completo nuevo sin modificar los históricos",
      description =
          "Solo Admin. Un peso por cada campo de daño obtenido en servidor; admite formularios publicados. Acta null identifica provisionales; una referencia no certifica firma. No recalcula inspecciones.")
  @ApiResponse(responseCode = "201", description = "Juego creado y auditado con sus pesos")
  @ApiResponse(
      responseCode = "422",
      description = "Campos incompletos, ajenos o esquema incompatible")
  ResponseEntity<JuegoPesosDTO> crear(
      @PathVariable UUID formularioId, @Valid @RequestBody CrearJuegoPesosDTO datos) {
    var juego = creacion.crear(formularioId, datos);
    return ResponseEntity.created(URI.create("/api/v1/juegos-pesos/" + juego.id())).body(juego);
  }

  @GetMapping("/formularios/{formularioId}/elementos-dano")
  @Operation(
      summary = "Consulta referencias de daño de una versión, aunque no tenga pesos",
      description =
          "Referencias canónicas ordenadas; no son rutas de respuestas ni pesos calibrados. page desde 0, size máximo 100.")
  @ApiResponse(responseCode = "200", description = "Página de referencias del esquema persistido")
  @ApiResponse(responseCode = "422", description = "Metadatos de daño incompatibles")
  PaginaPesosDTO<String> elementos(
      @PathVariable UUID formularioId,
      @Valid @ModelAttribute @ParameterObject PaginacionPesosDTO pagina) {
    return PaginaPesosDTO.de(elementos.listar(formularioId, pagina.page(), pagina.size()));
  }

  @GetMapping("/juegos-pesos/formularios")
  @Operation(
      summary = "Lista versiones de formulario con juegos de pesos guardados",
      description =
          "Solo referencias id/codigo, una por versión; incluye históricas, sin certificar calibración. Orden por codigo e id, page desde 0 y size máximo 100.")
  @ApiResponse(
      responseCode = "200",
      description = "Página de versiones con configuraciones, vacía si no hay")
  PaginaPesosDTO<FormularioConPesosDTO> formularios(
      @Valid @ModelAttribute @ParameterObject PaginacionPesosDTO pagina) {
    return PaginaPesosDTO.de(consulta.listarFormularios(pagina.page(), pagina.size()));
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
