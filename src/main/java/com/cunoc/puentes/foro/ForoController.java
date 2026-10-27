package com.cunoc.puentes.foro;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.foro.dto.ComentarioDTO;
import com.cunoc.puentes.foro.dto.CrearComentarioDTO;
import com.cunoc.puentes.foro.dto.CrearHiloDTO;
import com.cunoc.puentes.foro.dto.EditarComentarioDTO;
import com.cunoc.puentes.foro.dto.HiloDTO;
import com.cunoc.puentes.foro.dto.MotivoModeracionDTO;
import com.cunoc.puentes.foro.dto.PaginaComentariosDTO;
import com.cunoc.puentes.foro.dto.PaginaHilosDTO;
import com.cunoc.puentes.foro.dto.PaginaPuentesForoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Controlador REST del foro permanente por puente y de inspecciones publicadas (P9). */
@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Foro",
    description =
        "Foro permanente por puente y foro de inspecciones publicadas para discusión técnica y consultas")
class ForoController {

  private final ForoService servicio;

  ForoController(ForoService servicio) {
    this.servicio = servicio;
  }

  @Operation(
      summary = "Lista los puentes con acceso a su foro permanente",
      description =
          "Lectura pública. Permite buscar por nombre, código o ruta y conserva los foros históricos.")
  @ApiResponse(
      responseCode = "200",
      description = "Página de puentes y cantidad de hilos permanentes")
  @ApiResponse(responseCode = "400", description = "Búsqueda o paginación inválidas")
  @GetMapping("/foro/puentes")
  PaginaPuentesForoDTO listarForos(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size) {
    return servicio.listarForos(q, page, size);
  }

  @Operation(
      summary = "Lista los hilos del foro permanente de un puente",
      description =
          "Devuelve los hilos paginados en orden descendente estable por creadoEn/id."
              + " Filtra opcionalmente por categoría.")
  @ApiResponse(responseCode = "200", description = "Página de hilos")
  @ApiResponse(
      responseCode = "400",
      description =
          "Parámetros de paginación inválidos, desbordamiento o categoría fuera de catálogo")
  @ApiResponse(responseCode = "404", description = "Puente no encontrado")
  @GetMapping("/puentes/{puenteId}/hilos")
  PaginaHilosDTO listarHilos(
      @PathVariable UUID puenteId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @Parameter(
              description = "Categoría del hilo",
              schema = @Schema(implementation = CategoriaHilo.class))
          @RequestParam(required = false)
          String categoria) {
    return servicio.listarHilos(puenteId, page, size, parsearCategoria(categoria));
  }

  @Operation(
      summary = "Crea un nuevo hilo en el foro permanente del puente",
      description = "Requiere autenticación de cualquier rol. El autor proviene de la sesión.")
  @ApiResponse(responseCode = "201", description = "Hilo creado")
  @ApiResponse(
      responseCode = "400",
      description = "Título o categoría inválidos, o usuario autenticado no existe")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token vencido")
  @ApiResponse(responseCode = "404", description = "Puente no encontrado")
  @PostMapping("/puentes/{puenteId}/hilos")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  HiloDTO crearHilo(
      @PathVariable UUID puenteId, @Valid @RequestBody CrearHiloDTO dto, Principal usuario) {
    return servicio.crearHilo(puenteId, extraerUsuarioId(usuario), dto);
  }

  @Operation(
      summary = "Obtiene el detalle del hilo del foro de una inspección publicada",
      description = "Lectura pública del hilo asociado a la inspección publicada (RN-FOR-02).")
  @ApiResponse(responseCode = "200", description = "Detalle del hilo de la inspección")
  @ApiResponse(responseCode = "404", description = "Hilo de inspección no encontrado")
  @GetMapping("/inspecciones/{inspeccionId}/hilo")
  HiloDTO obtenerHiloInspeccion(@PathVariable UUID inspeccionId) {
    return servicio.obtenerHiloInspeccion(inspeccionId);
  }

  @Operation(
      summary = "Lista los comentarios raíz del foro de una inspección publicada",
      description =
          "Devuelve comentarios raíz en orden cronológico ascendente. Permite filtrar por elemento_ref (RN-FOR-03).")
  @ApiResponse(responseCode = "200", description = "Página de comentarios de la inspección")
  @ApiResponse(responseCode = "400", description = "Parámetros de paginación inválidos")
  @ApiResponse(responseCode = "404", description = "Hilo de inspección no encontrado")
  @GetMapping("/inspecciones/{inspeccionId}/comentarios")
  PaginaComentariosDTO listarComentariosInspeccion(
      @PathVariable UUID inspeccionId,
      @RequestParam(name = "elemento_ref", required = false) String elementoRef,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return servicio.listarComentariosInspeccion(inspeccionId, elementoRef, page, size);
  }

  @Operation(
      summary = "Publica un comentario o respuesta en el foro de una inspección publicada",
      description =
          "Requiere autenticación. Admite elemento_ref opcional (RN-FOR-03) y padre_id para respuestas de un nivel.")
  @ApiResponse(responseCode = "201", description = "Comentario creado")
  @ApiResponse(
      responseCode = "400",
      description = "Texto vacío, elemento_ref demasiado largo o usuario inválido")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token vencido")
  @ApiResponse(responseCode = "404", description = "Hilo o comentario padre no encontrado")
  @ApiResponse(responseCode = "409", description = "Hilo cerrado o anidamiento mayor a un nivel")
  @PostMapping("/inspecciones/{inspeccionId}/comentarios")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  ComentarioDTO crearComentarioInspeccion(
      @PathVariable UUID inspeccionId,
      @Valid @RequestBody CrearComentarioDTO dto,
      Principal usuario) {
    return servicio.crearComentarioInspeccion(inspeccionId, extraerUsuarioId(usuario), dto);
  }

  @Operation(
      summary = "Obtiene el detalle de un hilo del foro permanente",
      description = "Lectura pública. Hilos ligados a inspección devuelven 404 uniforme.")
  @ApiResponse(responseCode = "200", description = "Detalle del hilo")
  @ApiResponse(
      responseCode = "404",
      description = "Hilo inexistente o perteneciente a una inspección")
  @GetMapping("/hilos/{hiloId}")
  HiloDTO obtenerHilo(@PathVariable UUID hiloId) {
    return servicio.obtenerHilo(hiloId);
  }

  @Operation(
      summary = "Lista los comentarios raíz de un hilo del foro",
      description =
          "Devuelve comentarios raíz en orden cronológico ascendente (creadoEn asc, id asc)."
              + " Incluye comentarios eliminados como marca y filtra los ocultos.")
  @ApiResponse(responseCode = "200", description = "Página de comentarios raíz")
  @ApiResponse(
      responseCode = "400",
      description = "Parámetros de paginación inválidos o desbordamiento")
  @ApiResponse(
      responseCode = "404",
      description = "Hilo inexistente o perteneciente a una inspección")
  @GetMapping("/hilos/{hiloId}/comentarios")
  PaginaComentariosDTO listarComentarios(
      @PathVariable UUID hiloId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return servicio.listarComentarios(hiloId, page, size);
  }

  @Operation(
      summary = "Publica un nuevo comentario raíz o respuesta en un hilo del foro",
      description =
          "Requiere autenticación. Admite padre_id opcional para respuestas de un nivel."
              + " Rechaza comentarios si el hilo está cerrado.")
  @ApiResponse(responseCode = "201", description = "Comentario creado")
  @ApiResponse(responseCode = "400", description = "Texto vacío o usuario autenticado no existe")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token vencido")
  @ApiResponse(
      responseCode = "404",
      description =
          "Hilo inexistente o perteneciente a una inspección, o padre inexistente/oculto/eliminado")
  @ApiResponse(
      responseCode = "409",
      description = "El hilo está cerrado o el padre es a su vez una respuesta (anidamiento > 1)")
  @PostMapping("/hilos/{hiloId}/comentarios")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("isAuthenticated()")
  ComentarioDTO crearComentario(
      @PathVariable UUID hiloId, @Valid @RequestBody CrearComentarioDTO dto, Principal usuario) {
    return servicio.crearComentario(hiloId, extraerUsuarioId(usuario), dto);
  }

  @Operation(
      summary = "Lista las respuestas directas de un comentario raíz",
      description =
          "Lectura pública paginada en orden ascendente (creadoEn asc, id asc)."
              + " Incluye respuestas eliminadas como marca y filtra las ocultas.")
  @ApiResponse(responseCode = "200", description = "Página de respuestas")
  @ApiResponse(
      responseCode = "400",
      description = "Parámetros de paginación inválidos o desbordamiento")
  @ApiResponse(
      responseCode = "404",
      description =
          "Comentario raíz inexistente, oculto o perteneciente a una respuesta o inspección")
  @GetMapping("/comentarios/{id}/respuestas")
  PaginaComentariosDTO listarRespuestas(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return servicio.listarRespuestas(id, page, size);
  }

  @Operation(
      summary = "Edita el texto de un comentario propio",
      description =
          "Permitido únicamente al autor dentro de los 15 minutos siguientes a su publicación."
              + " Vencido el plazo devuelve 409.")
  @ApiResponse(responseCode = "200", description = "Comentario actualizado")
  @ApiResponse(responseCode = "400", description = "Texto vacío")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token vencido")
  @ApiResponse(
      responseCode = "404",
      description = "Comentario inexistente, de otro autor, oculto o eliminado")
  @ApiResponse(responseCode = "409", description = "Plazo de edición de 15 minutos vencido")
  @PatchMapping("/comentarios/{id}")
  @PreAuthorize("isAuthenticated()")
  ComentarioDTO editarComentario(
      @PathVariable UUID id, @Valid @RequestBody EditarComentarioDTO dto, Principal usuario) {
    return servicio.editarComentario(id, extraerUsuarioId(usuario), dto);
  }

  @Operation(
      summary = "Elimina lógicamente un comentario propio",
      description = "Marca el comentario como eliminado conservando sus respuestas. Idempotente.")
  @ApiResponse(responseCode = "204", description = "Comentario eliminado lógicamente")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token vencido")
  @ApiResponse(responseCode = "404", description = "Comentario inexistente, de otro autor o oculto")
  @DeleteMapping("/comentarios/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  void eliminarComentario(@PathVariable UUID id, Principal usuario) {
    servicio.eliminarComentario(id, extraerUsuarioId(usuario));
  }

  @Operation(
      summary = "Oculta un comentario por moderación",
      description =
          "Solo Catedrático o Administrador. Oculta el comentario y si es raíz, sus respuestas visibles.")
  @ApiResponse(responseCode = "204", description = "Comentario ocultado exitosamente o ya oculto")
  @ApiResponse(responseCode = "400", description = "Motivo obligatorio no proporcionado")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token vencido")
  @ApiResponse(
      responseCode = "403",
      description = "Sin rol de moderación Catedrático o Administrador")
  @ApiResponse(responseCode = "404", description = "Comentario inexistente o ligado a inspección")
  @PatchMapping("/comentarios/{id}/ocultamiento")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  void ocultarComentario(
      @PathVariable UUID id, @Valid @RequestBody MotivoModeracionDTO dto, Principal usuario) {
    extraerUsuarioId(usuario);
    servicio.ocultarComentario(id, dto.motivo());
  }

  @Operation(
      summary = "Cierra un hilo por moderación",
      description =
          "Solo Catedrático o Administrador. Cierra el hilo impidiendo nuevos comentarios.")
  @ApiResponse(responseCode = "204", description = "Hilo cerrado exitosamente o ya cerrado")
  @ApiResponse(responseCode = "400", description = "Motivo obligatorio no proporcionado")
  @ApiResponse(responseCode = "401", description = "Sin sesión o token vencido")
  @ApiResponse(
      responseCode = "403",
      description = "Sin rol de moderación Catedrático o Administrador")
  @ApiResponse(responseCode = "404", description = "Hilo inexistente o ligado a inspección")
  @PatchMapping("/hilos/{id}/cierre")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  void cerrarHilo(
      @PathVariable UUID id, @Valid @RequestBody MotivoModeracionDTO dto, Principal usuario) {
    extraerUsuarioId(usuario);
    servicio.cerrarHilo(id, dto.motivo());
  }

  private static UUID extraerUsuarioId(Principal usuario) {
    if (usuario == null || usuario.getName() == null) {
      throw new NegocioException(
          HttpStatus.UNAUTHORIZED,
          "sesion-invalida",
          "Sesión requerida",
          "No se encontró un usuario autenticado.");
    }
    try {
      return UUID.fromString(usuario.getName());
    } catch (IllegalArgumentException ex) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "identificador-usuario-invalido",
          "Identificador de usuario inválido",
          "El identificador del usuario autenticado no tiene formato UUID válido.");
    }
  }

  private static CategoriaHilo parsearCategoria(String categoria) {
    if (categoria == null || categoria.isBlank()) {
      return null;
    }
    try {
      return CategoriaHilo.desdeCodigo(categoria);
    } catch (IllegalArgumentException ex) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "categoria-invalida",
          "Categoría inválida",
          "La categoría especificada no pertenece al catálogo válido.");
    }
  }
}
