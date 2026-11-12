package com.cunoc.puentes.formulario;

import com.cunoc.puentes.common.ErrorCampo;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.formulario.dto.CampoExtraDTO;
import com.cunoc.puentes.formulario.dto.CrearFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.EditarFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.EsquemaBaseDTO;
import com.cunoc.puentes.formulario.dto.FormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PaginaFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PublicarFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.ResultadoValidacionFormularioDTO;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormularioVersionService {

  private final FormularioVersionRepository repository;
  private final FormularioVersionMapper mapper;
  private final FormularioValidacionServiceImpl validacionService;
  private final ComposicionEsquemaService composicion;
  private final ApplicationEventPublisher publicador;

  public FormularioVersionService(
      FormularioVersionRepository repository,
      FormularioVersionMapper mapper,
      FormularioValidacionServiceImpl validacionService,
      ComposicionEsquemaService composicion,
      ApplicationEventPublisher publicador) {
    this.repository = repository;
    this.mapper = mapper;
    this.validacionService = validacionService;
    this.composicion = composicion;
    this.publicador = publicador;
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

  /** B2: esquema base SIECA y secciones admitidas para el constructor de formularios. */
  public EsquemaBaseDTO esquemaBase() {
    return composicion.esquemaBase();
  }

  /** B2: validación en seco de campos adicionales; no guarda ni publica nada. */
  public ResultadoValidacionFormularioDTO validarComposicion(List<CampoExtraDTO> camposExtra) {
    List<ErrorCampo> errores = composicion.validar(camposExtra);
    return new ResultadoValidacionFormularioDTO(errores.isEmpty(), errores);
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

    Map<String, Object> esquema = resolverEsquema(datos.esquema(), datos.camposExtra());
    // Validar sintaxis del JSON Schema
    validacionService.compilar(esquema);

    FormularioVersion borrador = new FormularioVersion(datos.codigo().trim(), esquema);
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

    Map<String, Object> esquema = resolverEsquema(datos.esquema(), datos.camposExtra());
    // Validar sintaxis del esquema
    validacionService.compilar(esquema);

    version.setEsquema(esquema);
    validacionService.invalidarCache(id);

    return mapper.aDTO(repository.save(version));
  }

  /**
   * RN-FRM-04 y RN-FRM-06: Publicar versión, congelando la previa y declarando el mapeo. Publica
   * {@link FormularioPublicado} dentro de la misma transacción: el módulo de índice copia allí el
   * juego de pesos vigente (B2).
   */
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

    // Si existe una versión activa previa, se retira y se guarda inmediatamente en BD
    Optional<FormularioVersion> activaPrevia = repository.findByEstado(EstadoFormulario.ACTIVA);
    UUID anteriorId = activaPrevia.map(FormularioVersion::getId).orElse(null);
    if (activaPrevia.isPresent()) {
      FormularioVersion activa = activaPrevia.get();
      activa.retirar();
      repository.saveAndFlush(activa);
      validacionService.invalidarCache(activa.getId());
    }

    versionAPublicar.publicar(datos != null ? datos.mapeo() : null);
    validacionService.invalidarCache(id);

    FormularioVersionDTO publicada = mapper.aDTO(repository.saveAndFlush(versionAPublicar));
    publicador.publishEvent(new FormularioPublicado(anteriorId, id));
    return publicada;
  }

  /**
   * Los campos adicionales tienen prioridad: si llegan, el esquema se compone sobre la base. Sin
   * ellos se acepta un JSON Schema completo, como antes de B2.
   */
  private Map<String, Object> resolverEsquema(
      Map<String, Object> esquema, List<CampoExtraDTO> camposExtra) {
    if (camposExtra != null) {
      return composicion.componer(camposExtra);
    }
    if (esquema != null) {
      return esquema;
    }
    throw new NegocioException(
        HttpStatus.BAD_REQUEST,
        "esquema-requerido",
        "Falta el contenido del formulario",
        "Envíe campos_extra para componer el formulario o esquema con un JSON Schema completo.");
  }
}
