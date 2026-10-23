package com.cunoc.puentes.formulario;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.formulario.dto.CrearFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.EditarFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.FormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PaginaFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PublicarFormularioVersionDTO;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormularioVersionService {

  private final FormularioVersionRepository repository;
  private final FormularioVersionMapper mapper;
  private final FormularioValidacionServiceImpl validacionService;

  public FormularioVersionService(
      FormularioVersionRepository repository,
      FormularioVersionMapper mapper,
      FormularioValidacionServiceImpl validacionService) {
    this.repository = repository;
    this.mapper = mapper;
    this.validacionService = validacionService;
  }

  /** RN-FRM-04: Retorna la única versión de formulario activa. */
  @Transactional(readOnly = true)
  public FormularioVersionDTO obtenerActivo() {
    FormularioVersion activa =
        repository
            .findByEstado(EstadoFormulario.ACTIVA)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "formulario-activo-no-encontrado",
                        "Formulario activo no disponible",
                        "No se encontró ninguna versión de formulario marcada como ACTIVA."));
    return mapper.aDTO(activa);
  }

  /** RN-FRM-05: Retorna el esquema de una versión histórica por su código. */
  @Transactional(readOnly = true)
  public FormularioVersionDTO obtenerPorCodigo(String codigo) {
    FormularioVersion version =
        repository
            .findByCodigo(codigo)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "formulario-no-encontrado",
                        "Versión de formulario no encontrada",
                        "No existe una versión de formulario con código: " + codigo));
    return mapper.aDTO(version);
  }

  @Transactional(readOnly = true)
  public FormularioVersionDTO detalle(UUID id) {
    FormularioVersion version =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "formulario-no-encontrado",
                        "Versión de formulario no encontrada",
                        "No existe una versión de formulario con id: " + id));
    return mapper.aDTO(version);
  }

  @Transactional(readOnly = true)
  public PaginaFormularioVersionDTO listar(Pageable pageable) {
    return mapper.aPaginaDTO(repository.findAllByOrderByCreadoEnDesc(pageable));
  }

  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "formulario_version", entidadId = "#result.id")
  public FormularioVersionDTO crearBorrador(CrearFormularioVersionDTO datos) {
    if (repository.existsByCodigo(datos.codigo().trim())) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "codigo-formulario-duplicado",
          "Código de formulario ya existe",
          "Ya existe una versión con el código: " + datos.codigo());
    }

    // Validar sintaxis del JSON Schema
    validacionService.validarConEsquema(datos.esquema(), java.util.Map.of());

    FormularioVersion borrador = new FormularioVersion(datos.codigo().trim(), datos.esquema());
    return mapper.aDTO(repository.save(borrador));
  }

  /** RN-FRM-03: Una versión publicada es inmutable; solo se pueden editar borradores. */
  @Transactional
  @Auditable(accion = Acciones.MODIFICAR, entidad = "formulario_version", entidadId = "#id")
  public FormularioVersionDTO editarBorrador(UUID id, EditarFormularioVersionDTO datos) {
    FormularioVersion version =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "formulario-no-encontrado",
                        "Versión de formulario no encontrada",
                        "No existe una versión con id: " + id));

    if (version.getEstado() != EstadoFormulario.BORRADOR) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "formulario-inmutable",
          "Versión inmutable",
          "No se puede modificar una versión que se encuentra en estado " + version.getEstado());
    }

    // Validar sintaxis del esquema
    validacionService.validarConEsquema(datos.esquema(), java.util.Map.of());

    version.setEsquema(datos.esquema());
    version.setActualizadoEn(Instant.now());
    validacionService.invalidarCache(id);

    return mapper.aDTO(repository.save(version));
  }

  /** RN-FRM-04 y RN-FRM-06: Publicar versión, congelando la previa y declarando el mapeo. */
  @Transactional
  @Auditable(accion = Acciones.CAMBIAR_ESTADO, entidad = "formulario_version", entidadId = "#id")
  public FormularioVersionDTO publicar(UUID id, PublicarFormularioVersionDTO datos) {
    FormularioVersion versionAPublicar =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "formulario-no-encontrado",
                        "Versión de formulario no encontrada",
                        "No existe una versión con id: " + id));

    if (versionAPublicar.getEstado() != EstadoFormulario.BORRADOR) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "estado-invalido",
          "Estado inválido para publicación",
          "Solo se pueden publicar versiones en estado BORRADOR. Estado actual: "
              + versionAPublicar.getEstado());
    }

    // Si existe una versión activa previa, se retira
    Optional<FormularioVersion> activaPrevia = repository.findByEstado(EstadoFormulario.ACTIVA);
    activaPrevia.ifPresent(
        activa -> {
          activa.retirar();
          activa.setActualizadoEn(Instant.now());
          repository.save(activa);
          validacionService.invalidarCache(activa.getId());
        });

    versionAPublicar.publicar(datos != null ? datos.mapeo() : null);
    versionAPublicar.setActualizadoEn(Instant.now());
    validacionService.invalidarCache(id);

    return mapper.aDTO(repository.save(versionAPublicar));
  }
}
