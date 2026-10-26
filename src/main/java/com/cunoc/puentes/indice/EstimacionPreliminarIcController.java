package com.cunoc.puentes.indice;

import com.cunoc.puentes.indice.dto.ConfirmacionEstadoDTO;
import com.cunoc.puentes.indice.dto.EstimacionPreliminarIcDTO;
import com.cunoc.puentes.indice.dto.ResultadoConfirmacionEstadoDTO;
import com.cunoc.puentes.indice.dto.SolicitudEstimacionPreliminarDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ESTUDIANTE', 'CATEDRATICO', 'ADMINISTRADOR', 'PROFESIONAL_EXTERNO')")
@Tag(
    name = "Índice de Condición - Estimación y Confirmación",
    description =
        "Estimación preliminar en borradores (RN-IC-07) y confirmación de estado con justificación (RN-IC-06)")
@ApiResponse(responseCode = "401", description = "Sin autenticación o con credenciales vencidas")
@ApiResponse(responseCode = "403", description = "Rol sin autorización para evaluar inspecciones")
class EstimacionPreliminarIcController {

  private final EstimacionPreliminarIcService servicio;

  EstimacionPreliminarIcController(EstimacionPreliminarIcService servicio) {
    this.servicio = servicio;
  }

  @PostMapping("/formularios/{formularioId}/ic-preliminar")
  @Operation(
      summary = "Estima el IC preliminar a partir de respuestas de formulario (RN-IC-07)",
      description =
          "Calcula una estimación del IC rotulada explícitamente como preliminar. "
              + "No persiste resultados ni altera la condición del inventario de puentes.")
  @ApiResponse(responseCode = "200", description = "Estimación preliminar calculada")
  @ApiResponse(
      responseCode = "422",
      description = "Respuestas incompletas o sin juegos de pesos configurados")
  EstimacionPreliminarIcDTO estimarPorFormulario(
      @PathVariable UUID formularioId,
      @Valid @RequestBody SolicitudEstimacionPreliminarDTO solicitud) {
    var criticas =
        solicitud.condicionesCriticas() != null ? solicitud.condicionesCriticas().aDominio() : null;
    return servicio.estimar(formularioId, solicitud.versionPesosId(), solicitud.datos(), criticas);
  }

  @PostMapping("/inspecciones/{inspeccionId}/ic-preliminar")
  @Operation(
      summary = "Estima el IC preliminar de un borrador de inspección (RN-IC-07)",
      description =
          "Calcula el IC preliminar para una inspección no publicada. Si ya está publicada, "
              + "rechaza con 409 Conflict pues su IC ya es oficial.")
  @ApiResponse(responseCode = "200", description = "Estimación preliminar de la inspección")
  @ApiResponse(responseCode = "404", description = "La inspección no existe")
  @ApiResponse(responseCode = "409", description = "La inspección ya está publicada")
  @ApiResponse(responseCode = "422", description = "Respuestas guardadas no interpretables")
  EstimacionPreliminarIcDTO estimarPorInspeccion(
      @PathVariable UUID inspeccionId, @RequestParam(required = false) UUID versionPesosId) {
    return servicio.estimarParaInspeccion(inspeccionId, versionPesosId);
  }

  @PostMapping("/inspecciones/confirmacion-estado/validacion")
  @Operation(
      summary = "Valida la confirmación del estado de condición y su justificación (RN-IC-06)",
      description =
          "Verifica que si el estado confirmado difiere del calculado, se proporcione una justificación "
              + "no vacía. Normaliza espacios y reporta si hay elevación de una anulación crítica.")
  @ApiResponse(responseCode = "200", description = "Confirmación válida y normalizada")
  @ApiResponse(
      responseCode = "400",
      description = "Justificación ausente o vacía ante estados discrepantes")
  ResultadoConfirmacionEstadoDTO validarConfirmacion(
      @Valid @RequestBody ConfirmacionEstadoDTO solicitud) {
    return servicio.validarConfirmacion(solicitud);
  }
}
