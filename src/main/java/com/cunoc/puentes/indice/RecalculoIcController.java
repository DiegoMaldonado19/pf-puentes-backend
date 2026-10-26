package com.cunoc.puentes.indice;

import com.cunoc.puentes.indice.dto.ComparacionRecalculoIcDTO;
import com.cunoc.puentes.indice.dto.PaginacionPesosDTO;
import com.cunoc.puentes.indice.dto.RecalculoIcDTO;
import com.cunoc.puentes.indice.dto.SolicitarRecalculoIcDTO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspecciones/{inspeccionId}/recalculos-ic")
@PreAuthorize("hasAnyRole('CATEDRATICO', 'ADMINISTRADOR')")
@Tag(
    name = "Recálculos del IC",
    description = "Comparación y registro fechado bajo otros juegos de pesos (RN-IC-08)")
@ApiResponse(responseCode = "401", description = "Sin sesión o con el token vencido")
@ApiResponse(responseCode = "403", description = "Solo Catedrático o Administrador")
@ApiResponse(responseCode = "400", description = "UUID, cuerpo o paginación inválidos")
@ApiResponse(responseCode = "404", description = "La inspección o el juego de pesos no existen")
@ApiResponse(responseCode = "409", description = "La inspección no está en estado PUBLICADA")
class RecalculoIcController {

  private final RecalculoIcService servicio;

  RecalculoIcController(RecalculoIcService servicio) {
    this.servicio = servicio;
  }

  @GetMapping
  @Operation(
      summary = "Compara el resultado publicado original con sus recálculos fechados (RN-IC-08)",
      description =
          "Devuelve el resultado oficial inmutable de la inspección publicada junto con la página "
              + "de recálculos ordenada por ID descendente. page desde 0; size por defecto 20, máximo 100.")
  @ApiResponse(responseCode = "200", description = "Resultado original y página de recálculos")
  ComparacionRecalculoIcDTO consultar(
      @PathVariable UUID inspeccionId,
      @Valid @ModelAttribute @ParameterObject PaginacionPesosDTO pagina) {
    return servicio.consultar(inspeccionId, pagina.page(), pagina.size());
  }

  @PostMapping
  @Operation(
      summary = "Registra o recupera un recálculo fechado bajo otro juego de pesos (RN-IC-08)",
      description =
          "Calcula el IC con el juego indicado de la misma versión de formulario sin modificar la "
              + "inspección publicada ni los campos derivados del puente. Idempotente por pareja "
              + "(inspección, juego de pesos).")
  @ApiResponse(responseCode = "200", description = "Recálculo registrado o recuperado")
  @ApiResponse(
      responseCode = "422",
      description = "Juego incompatible o respuestas de daño inválidas")
  RecalculoIcDTO recalcular(
      @PathVariable UUID inspeccionId, @Valid @RequestBody SolicitarRecalculoIcDTO datos) {
    return servicio.recalcular(inspeccionId, datos.versionPesosId());
  }
}

