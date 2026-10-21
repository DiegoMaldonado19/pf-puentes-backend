package com.cunoc.puentes.mantenimiento;

import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import com.cunoc.puentes.archivo.dto.SubirFotoDTO;
import com.cunoc.puentes.mantenimiento.dto.CierreOrdenDTO;
import com.cunoc.puentes.mantenimiento.dto.OrdenMantenimientoDTO;
import com.cunoc.puentes.mantenimiento.dto.PaginaEvidenciasDTO;
import com.cunoc.puentes.mantenimiento.dto.PaginacionOrdenesDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/puentes/{puenteId}/ordenes-mantenimiento/{ordenId}")
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
@Tag(name = "Mantenimiento")
@ApiResponse(responseCode = "400", description = "UUID, datos o paginación inválidos")
@ApiResponse(responseCode = "401", description = "Sin sesión o token inválido")
@ApiResponse(responseCode = "403", description = "Solo Catedrático y Administrador")
@ApiResponse(responseCode = "404", description = "La orden no existe en el puente indicado")
class EvidenciaYCierreController {
  private final EvidenciasOrdenService evidencias;
  private final OrdenMantenimientoService ordenes;

  EvidenciaYCierreController(EvidenciasOrdenService evidencias, OrdenMantenimientoService ordenes) {
    this.evidencias = evidencias;
    this.ordenes = ordenes;
  }

  @PostMapping(path = "/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Guarda foto y miniatura de evidencia en PROGRAMADA o EN_EJECUCION",
      description =
          "Contrato 16 de P1. UUID del cliente, foto, miniatura y capturada_en obligatorios; "
              + "ubicación y elemento_ref opcionales. WebP/JPEG por firma binaria. Repetir el UUID "
              + "de esta orden devuelve lo guardado sin duplicar archivo ni auditoría mientras sea editable.")
  @ApiResponse(responseCode = "201", description = "Foto guardada con URL firmadas temporales")
  @ApiResponse(responseCode = "409", description = "Estado incompatible o UUID usado en otro padre")
  @ApiResponse(responseCode = "413", description = "Archivo o petición demasiado grandes")
  @ApiResponse(
      responseCode = "422",
      description = "Firma de foto/miniatura inválida u origen inconsistente")
  ArchivoDTO subir(
      @PathVariable UUID puenteId,
      @PathVariable UUID ordenId,
      @Valid @ModelAttribute SubirFotoDTO datos) {
    return evidencias.subir(puenteId, ordenId, datos);
  }

  @GetMapping("/evidencias")
  @Operation(
      summary = "Consulta fotos guardadas de la orden, incluso después del cierre",
      description =
          "page desde 0, size 20 por defecto, máximo 100; orden creado_en e id ascendente. "
              + "Solo JPEG/WebP no purgadas. URL firmadas de P1: volver a consultar para renovarlas.")
  @ApiResponse(responseCode = "200", description = "Página con puente_id, orden_id y totales")
  PaginaEvidenciasDTO listar(
      @PathVariable UUID puenteId,
      @PathVariable UUID ordenId,
      @Valid @ModelAttribute @ParameterObject PaginacionOrdenesDTO pagina) {
    return evidencias.listar(puenteId, ordenId, pagina.page(), pagina.size());
  }

  @PostMapping(path = "/cierre", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Cierra EN_EJECUCION como EJECUTADA con fecha, responsable y evidencia",
      description =
          "RN-MTO-05. El servidor cuenta las fotos guardadas de esta orden. "
              + "No acepta un contador del cliente. Conserva el resto de datos; audita el cambio "
              + "en la misma transacción. Responsable: hasta 150 caracteres Unicode. "
              + "No se imponen restricciones de fecha no especificadas por el enunciado.")
  @ApiResponse(responseCode = "200", description = "Orden persistida en EJECUTADA")
  @ApiResponse(responseCode = "409", description = "La orden ya no está EN_EJECUCION")
  @ApiResponse(
      responseCode = "422",
      description = "Falta foto guardada, responsable largo u origen inconsistente")
  OrdenMantenimientoDTO cerrar(
      @PathVariable UUID puenteId,
      @PathVariable UUID ordenId,
      @Valid @RequestBody CierreOrdenDTO datos) {
    return ordenes.cerrar(puenteId, ordenId, datos);
  }
}
