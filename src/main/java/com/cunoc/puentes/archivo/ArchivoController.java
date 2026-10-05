package com.cunoc.puentes.archivo;

import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import com.cunoc.puentes.archivo.dto.SubirDocumentoDTO;
import com.cunoc.puentes.archivo.dto.SubirFotoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ESTUDIANTE', 'PROFESIONAL_EXTERNO', 'CATEDRATICO')")
@Tag(name = "Archivos", description = "Fotos y documentos PDF de las inspecciones (RN-ARC)")
@ApiResponse(responseCode = "401", description = "Sin sesión o con el token vencido")
@ApiResponse(responseCode = "403", description = "Su rol no sube ni borra archivos")
@ApiResponse(
    responseCode = "404",
    description = "La inspección o el archivo no existen, o son de otro autor")
class ArchivoController {

  private final ArchivoService servicio;

  ArchivoController(ArchivoService servicio) {
    this.servicio = servicio;
  }

  @Operation(
      summary = "Sube una foto y su miniatura a un borrador propio",
      description = "Reenviar el mismo id devuelve la foto ya guardada (DT-OFF-05, DT-OFF-07).")
  @ApiResponse(responseCode = "201", description = "Foto guardada")
  @ApiResponse(responseCode = "400", description = "Falta un campo o su formato no es válido")
  @ApiResponse(
      responseCode = "409",
      description = "La inspección no es un borrador, o el id es de otra inspección")
  @ApiResponse(responseCode = "413", description = "La foto o la petición son demasiado grandes")
  @ApiResponse(
      responseCode = "422",
      description = "No es WebP ni JPEG, o la inspección ya tiene 60 fotos (RN-ARC-04)")
  @PostMapping(
      path = "/inspecciones/{inspeccionId}/fotos",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  ArchivoDTO subirFoto(
      @PathVariable UUID inspeccionId,
      @Valid @ModelAttribute SubirFotoDTO datos,
      Principal usuario) {
    return servicio.guardarFoto(inspeccionId, idDe(usuario), datos);
  }

  @Operation(
      summary = "Adjunta un PDF a un borrador propio",
      description =
          "Reenviar el mismo id devuelve el documento ya guardado (DT-OFF-05, DT-OFF-07).")
  @ApiResponse(responseCode = "201", description = "Documento guardado")
  @ApiResponse(responseCode = "400", description = "Falta un campo o su formato no es válido")
  @ApiResponse(
      responseCode = "409",
      description = "La inspección no es un borrador, o el id es de otra inspección")
  @ApiResponse(responseCode = "413", description = "El PDF pasa de 20 MB")
  @ApiResponse(
      responseCode = "422",
      description = "No es PDF, o la inspección ya tiene 10 documentos (RN-ARC-04)")
  @PostMapping(
      path = "/inspecciones/{inspeccionId}/documentos",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  ArchivoDTO subirDocumento(
      @PathVariable UUID inspeccionId,
      @Valid @ModelAttribute SubirDocumentoDTO datos,
      Principal usuario) {
    return servicio.guardarDocumento(inspeccionId, idDe(usuario), datos);
  }

  @Operation(summary = "Borra una foto o un PDF de un borrador propio")
  @ApiResponse(responseCode = "204", description = "Archivo borrado")
  @ApiResponse(
      responseCode = "409",
      description =
          "La inspección ya no es un borrador; la evidencia publicada no se borra (RN-ARC-05)")
  @DeleteMapping("/archivos/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void eliminar(@PathVariable UUID id, Principal usuario) {
    servicio.eliminar(id, idDe(usuario));
  }

  // Contrato 14: el nombre de la autenticación es el UUID del usuario
  private static UUID idDe(Principal usuario) {
    return UUID.fromString(usuario.getName());
  }
}
