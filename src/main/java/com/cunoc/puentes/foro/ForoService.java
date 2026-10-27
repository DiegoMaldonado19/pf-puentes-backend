package com.cunoc.puentes.foro;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.foro.dto.ComentarioDTO;
import com.cunoc.puentes.foro.dto.CrearComentarioDTO;
import com.cunoc.puentes.foro.dto.CrearHiloDTO;
import com.cunoc.puentes.foro.dto.EditarComentarioDTO;
import com.cunoc.puentes.foro.dto.HiloDTO;
import com.cunoc.puentes.foro.dto.PaginaComentariosDTO;
import com.cunoc.puentes.foro.dto.PaginaHilosDTO;
import com.cunoc.puentes.foro.dto.PaginaPuentesForoDTO;
import com.cunoc.puentes.foro.dto.PuenteForoDTO;
import com.cunoc.puentes.foro.dto.ResultadoModeracion;
import com.cunoc.puentes.inspeccion.InspeccionPublicada;
import com.cunoc.puentes.notificacion.NotificacionService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ForoService {

  static final int MAX_TAMANO_PAGINA = 100;
  static final long MAX_OFFSET_SOPORTADO = Integer.MAX_VALUE;
  static final int MAX_LONGITUD_TITULO = 200;

  static final String TIPO_NOTIFICACION_ALERTA_PUENTE = "foro.alerta.puente";
  static final String TIPO_NOTIFICACION_MENCION = "foro.mencion";
  static final String MENSAJE_MENCION_FORO = "Te mencionaron en un comentario del foro";

  private static final Pattern PATRON_MENCION =
      Pattern.compile(
          "(?<!\\w)@\\{?([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\}?");

  private final HiloRepository hiloRepositorio;
  private final ComentarioRepository comentarioRepositorio;
  private final ForoMapper mapper;
  private final EntityManager entityManager;
  private final NotificacionService notificacionService;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  ForoService(
      HiloRepository hiloRepositorio,
      ComentarioRepository comentarioRepositorio,
      ForoMapper mapper,
      EntityManager entityManager,
      NotificacionService notificacionService,
      Clock clock) {
    this.hiloRepositorio = hiloRepositorio;
    this.comentarioRepositorio = comentarioRepositorio;
    this.mapper = mapper;
    this.entityManager = entityManager;
    this.notificacionService = notificacionService;
    this.clock = clock;
  }

  ForoService(
      HiloRepository hiloRepositorio,
      ComentarioRepository comentarioRepositorio,
      ForoMapper mapper,
      EntityManager entityManager,
      Clock clock) {
    this(hiloRepositorio, comentarioRepositorio, mapper, entityManager, null, clock);
  }

  ForoService(
      HiloRepository hiloRepositorio,
      ComentarioRepository comentarioRepositorio,
      ForoMapper mapper,
      EntityManager entityManager) {
    this(hiloRepositorio, comentarioRepositorio, mapper, entityManager, null, Clock.systemUTC());
  }

  @Transactional(readOnly = true)
  public PaginaPuentesForoDTO listarForos(String busqueda, int page, int size) {
    validarPaginacion(page, size);
    String termino = busqueda == null ? "" : busqueda.trim();
    if (termino.length() > 120) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "busqueda-invalida",
          "Búsqueda inválida",
          "La búsqueda admite un máximo de 120 caracteres.");
    }
    Page<HiloRepository.PuenteResumen> resultado =
        hiloRepositorio.listarPuentesForo(termino, PageRequest.of(page, size));
    List<PuenteForoDTO> contenido =
        resultado.stream()
            .map(
                p ->
                    new PuenteForoDTO(
                        p.getId(),
                        p.getCodigo(),
                        p.getNombre(),
                        p.getRuta(),
                        p.getMunicipio(),
                        p.getDepartamento(),
                        p.getEstadoActual(),
                        p.getCantidadHilos()))
            .toList();
    return new PaginaPuentesForoDTO(
        contenido,
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  @Transactional(readOnly = true)
  public PaginaHilosDTO listarHilos(UUID puenteId, int page, int size, CategoriaHilo categoria) {
    validarPaginacion(page, size);

    if (!hiloRepositorio.existePuente(puenteId)) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "puente-no-encontrado",
          "Puente no encontrado",
          "El puente especificado no existe en el sistema.");
    }

    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));

    Page<Hilo> resultado =
        categoria != null
            ? hiloRepositorio.findByPuenteIdAndInspeccionIdIsNullAndCategoria(
                puenteId, categoria, pageable)
            : hiloRepositorio.findByPuenteIdAndInspeccionIdIsNull(puenteId, pageable);

    Map<UUID, String> autores = nombresAutores(resultado.stream().map(Hilo::getAutorId));
    List<HiloDTO> contenido =
        resultado.stream()
            .map(hilo -> mapper.aDTO(hilo).conAutorNombre(autores.get(hilo.getAutorId())))
            .toList();

    return new PaginaHilosDTO(
        contenido,
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  public HiloDTO crearHilo(UUID puenteId, UUID autorId, CrearHiloDTO dto) {
    if (dto == null || dto.titulo() == null || dto.titulo().isBlank()) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "titulo-vacio",
          "Título inválido",
          "El título del hilo no puede estar vacío.");
    }
    if (dto.titulo().trim().length() > MAX_LONGITUD_TITULO) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "titulo-demasiado-largo",
          "Título demasiado largo",
          "El título del hilo no puede superar los 200 caracteres.");
    }
    if (dto.categoria() == null) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "categoria-obligatoria",
          "Categoría obligatoria",
          "La categoría del hilo es obligatoria.");
    }

    if (!hiloRepositorio.existePuente(puenteId)) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "puente-no-encontrado",
          "Puente no encontrado",
          "El puente especificado no existe en el sistema.");
    }

    if (!hiloRepositorio.existeUsuario(autorId)) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "autor-invalido",
          "Autor inválido",
          "El autor autenticado no existe en el sistema.");
    }

    Hilo hilo = new Hilo();
    hilo.setPuenteId(puenteId);
    hilo.setAutorId(autorId);
    hilo.setTitulo(dto.titulo().trim());
    hilo.setCategoria(dto.categoria());

    Hilo guardado = hiloRepositorio.saveAndFlush(hilo);
    entityManager.refresh(guardado);
    notificarAlertaPuenteMalo(puenteId, guardado);
    return conAutor(guardado);
  }

  public Optional<HiloDTO> abrirHiloDeInspeccionPublicada(InspeccionPublicada evento) {
    validarEventoInspeccionPublicada(evento);

    if (!hiloRepositorio.existePuente(evento.puenteId())) {
      throw new IllegalStateException("El puente " + evento.puenteId() + " no existe.");
    }

    Optional<HiloRepository.InspeccionResumen> insOpt =
        hiloRepositorio.buscarInspeccion(evento.inspeccionId());
    if (insOpt.isEmpty()) {
      return Optional.empty();
    }

    HiloRepository.InspeccionResumen ins = insOpt.get();
    if (!evento.puenteId().equals(ins.getPuenteId())) {
      throw new IllegalStateException("La inspección no pertenece al puente del evento.");
    }
    if (!"PUBLICADA".equals(ins.getEstado())) {
      throw new IllegalStateException("La inspección no está en estado PUBLICADA.");
    }

    Optional<Hilo> existente = hiloRepositorio.findByInspeccionId(evento.inspeccionId());
    if (existente.isPresent()) {
      return existente.map(this::conAutor);
    }

    Hilo hilo = new Hilo();
    hilo.setPuenteId(evento.puenteId());
    hilo.setInspeccionId(evento.inspeccionId());
    hilo.setAutorId(null);
    hilo.setCategoria(CategoriaHilo.DISCUSION_TECNICA);
    hilo.setTitulo("Inspección publicada (" + evento.fecha() + ")");

    Hilo guardado = hiloRepositorio.saveAndFlush(hilo);
    entityManager.refresh(guardado);
    return Optional.of(conAutor(guardado));
  }

  @Transactional(readOnly = true)
  public HiloDTO obtenerHiloInspeccion(UUID inspeccionId) {
    Hilo hilo =
        hiloRepositorio
            .findByInspeccionId(inspeccionId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "hilo-no-encontrado",
                        "Hilo no encontrado",
                        "El hilo de la inspección solicitada no existe."));
    return conAutor(hilo);
  }

  @Transactional(readOnly = true)
  public PaginaComentariosDTO listarComentariosInspeccion(
      UUID inspeccionId, String elementoRef, int page, int size) {
    validarPaginacion(page, size);

    Hilo hilo =
        hiloRepositorio
            .findByInspeccionId(inspeccionId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "hilo-no-encontrado",
                        "Hilo no encontrado",
                        "El hilo de la inspección solicitada no existe."));

    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.asc("creadoEn"), Sort.Order.asc("id")));

    Page<Comentario> resultado =
        elementoRef != null && !elementoRef.isBlank()
            ? comentarioRepositorio.listarRaizInspeccionVisiblesPorElemento(
                hilo.getId(), elementoRef.trim(), pageable)
            : comentarioRepositorio.listarRaizInspeccionVisibles(hilo.getId(), pageable);

    Map<UUID, String> autores = nombresAutores(resultado.stream().map(Comentario::getAutorId));
    List<ComentarioDTO> contenido =
        resultado.stream()
            .map(
                comentario ->
                    mapper.aDTO(comentario).conAutorNombre(autores.get(comentario.getAutorId())))
            .toList();

    return new PaginaComentariosDTO(
        contenido,
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  public ComentarioDTO crearComentarioInspeccion(
      UUID inspeccionId, UUID autorId, CrearComentarioDTO dto) {
    if (dto == null || dto.texto() == null || dto.texto().isBlank()) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "texto-vacio",
          "Texto inválido",
          "El texto del comentario no puede estar vacío.");
    }

    String elementoRef =
        dto.elementoRef() != null && !dto.elementoRef().isBlank() ? dto.elementoRef().trim() : null;
    if (elementoRef != null && elementoRef.length() > MAX_LONGITUD_TITULO) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "elemento-ref-demasiado-largo",
          "Referencia de elemento demasiado larga",
          "La referencia del elemento no puede superar los 200 caracteres.");
    }

    if (!hiloRepositorio.existeUsuario(autorId)) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "autor-invalido",
          "Autor inválido",
          "El autor autenticado no existe en el sistema.");
    }

    Hilo hilo =
        hiloRepositorio
            .findByInspeccionId(inspeccionId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "hilo-no-encontrado",
                        "Hilo no encontrado",
                        "El hilo de la inspección solicitada no existe."));

    if (hilo.getCerradoEn() != null) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "hilo-cerrado",
          "Hilo cerrado",
          "No se pueden publicar comentarios en un hilo cerrado.");
    }

    Comentario respuestaA = null;
    if (dto.padreId() != null) {
      Comentario padre =
          comentarioRepositorio
              .buscarParaModificar(dto.padreId())
              .orElseThrow(
                  () ->
                      new NegocioException(
                          HttpStatus.NOT_FOUND,
                          "comentario-no-encontrado",
                          "Comentario no encontrado",
                          "El comentario padre no existe."));

      if (!hilo.getId().equals(padre.getHilo().getId())
          || padre.getOcultoEn() != null
          || padre.getEliminadoEn() != null) {
        throw new NegocioException(
            HttpStatus.NOT_FOUND,
            "comentario-no-encontrado",
            "Comentario no encontrado",
            "El comentario padre no existe o no está disponible para responder.");
      }

      if (padre.getRespuestaA() != null) {
        throw new NegocioException(
            HttpStatus.CONFLICT,
            "anidamiento-invalido",
            "Anidamiento inválido",
            "Los comentarios solo admiten un nivel de anidamiento; no se puede responder a una respuesta.");
      }

      respuestaA = padre;
      if (elementoRef == null) {
        elementoRef = padre.getElementoRef();
      }
    }

    Comentario comentario = new Comentario();
    comentario.setHilo(hilo);
    comentario.setAutorId(autorId);
    comentario.setTexto(dto.texto().trim());
    comentario.setRespuestaA(respuestaA);
    comentario.setElementoRef(elementoRef);

    Comentario guardado = comentarioRepositorio.saveAndFlush(comentario);
    entityManager.refresh(guardado);
    notificarMenciones(hilo, autorId, guardado.getTexto(), null);
    return conAutor(guardado);
  }

  @Transactional(readOnly = true)
  public HiloDTO obtenerHilo(UUID hiloId) {
    Hilo hilo =
        hiloRepositorio
            .findByIdAndInspeccionIdIsNull(hiloId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "hilo-no-encontrado",
                        "Hilo no encontrado",
                        "El hilo solicitado no existe o no pertenece al foro permanente."));

    return conAutor(hilo);
  }

  @Transactional(readOnly = true)
  public PaginaComentariosDTO listarComentarios(UUID hiloId, int page, int size) {
    validarPaginacion(page, size);

    if (!hiloRepositorio.existsByIdAndInspeccionIdIsNull(hiloId)) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "hilo-no-encontrado",
          "Hilo no encontrado",
          "El hilo solicitado no existe o no pertenece al foro permanente.");
    }

    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.asc("creadoEn"), Sort.Order.asc("id")));

    Page<Comentario> resultado = comentarioRepositorio.listarRaizVisibles(hiloId, pageable);
    Map<UUID, String> autores = nombresAutores(resultado.stream().map(Comentario::getAutorId));
    List<ComentarioDTO> contenido =
        resultado.stream()
            .map(
                comentario ->
                    mapper.aDTO(comentario).conAutorNombre(autores.get(comentario.getAutorId())))
            .toList();

    return new PaginaComentariosDTO(
        contenido,
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  public ComentarioDTO crearComentario(UUID hiloId, UUID autorId, CrearComentarioDTO dto) {
    if (dto == null || dto.texto() == null || dto.texto().isBlank()) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "texto-vacio",
          "Texto inválido",
          "El texto del comentario no puede estar vacío.");
    }

    if (!hiloRepositorio.existeUsuario(autorId)) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "autor-invalido",
          "Autor inválido",
          "El autor autenticado no existe en el sistema.");
    }

    Hilo hilo =
        hiloRepositorio
            .findByIdAndInspeccionIdIsNull(hiloId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "hilo-no-encontrado",
                        "Hilo no encontrado",
                        "El hilo solicitado no existe o no pertenece al foro permanente."));

    if (hilo.getCerradoEn() != null) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "hilo-cerrado",
          "Hilo cerrado",
          "No se pueden publicar comentarios en un hilo cerrado.");
    }

    Comentario respuestaA = null;
    if (dto.padreId() != null) {
      Comentario padre =
          comentarioRepositorio
              .buscarParaModificar(dto.padreId())
              .orElseThrow(
                  () ->
                      new NegocioException(
                          HttpStatus.NOT_FOUND,
                          "comentario-no-encontrado",
                          "Comentario no encontrado",
                          "El comentario padre no existe."));

      if (!hiloId.equals(padre.getHilo().getId())
          || padre.getHilo().getInspeccionId() != null
          || padre.getOcultoEn() != null
          || padre.getEliminadoEn() != null) {
        throw new NegocioException(
            HttpStatus.NOT_FOUND,
            "comentario-no-encontrado",
            "Comentario no encontrado",
            "El comentario padre no existe o no está disponible para responder.");
      }

      if (padre.getRespuestaA() != null) {
        throw new NegocioException(
            HttpStatus.CONFLICT,
            "anidamiento-invalido",
            "Anidamiento inválido",
            "Los comentarios solo admiten un nivel de anidamiento; no se puede responder a una respuesta.");
      }

      respuestaA = padre;
    }

    Comentario comentario = new Comentario();
    comentario.setHilo(hilo);
    comentario.setAutorId(autorId);
    comentario.setTexto(dto.texto().trim());
    comentario.setRespuestaA(respuestaA);

    Comentario guardado = comentarioRepositorio.saveAndFlush(comentario);
    entityManager.refresh(guardado);
    notificarMenciones(hilo, autorId, guardado.getTexto(), null);
    return conAutor(guardado);
  }

  @Transactional(readOnly = true)
  public PaginaComentariosDTO listarRespuestas(UUID comentarioId, int page, int size) {
    validarPaginacion(page, size);

    Comentario raiz =
        comentarioRepositorio
            .findById(comentarioId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "comentario-no-encontrado",
                        "Comentario no encontrado",
                        "El comentario solicitado no existe."));

    if (raiz.getHilo().getInspeccionId() != null
        || raiz.getOcultoEn() != null
        || raiz.getRespuestaA() != null) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "comentario-no-encontrado",
          "Comentario no encontrado",
          "El comentario raíz no existe o no admite respuestas.");
    }

    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.asc("creadoEn"), Sort.Order.asc("id")));

    Page<Comentario> resultado =
        comentarioRepositorio.listarRespuestasVisibles(comentarioId, pageable);
    Map<UUID, String> autores = nombresAutores(resultado.stream().map(Comentario::getAutorId));
    List<ComentarioDTO> contenido =
        resultado.stream()
            .map(
                comentario ->
                    mapper.aDTO(comentario).conAutorNombre(autores.get(comentario.getAutorId())))
            .toList();

    return new PaginaComentariosDTO(
        contenido,
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  public ComentarioDTO editarComentario(UUID comentarioId, UUID autorId, EditarComentarioDTO dto) {
    if (dto == null || dto.texto() == null || dto.texto().isBlank()) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "texto-vacio",
          "Texto inválido",
          "El texto del comentario no puede estar vacío.");
    }

    Comentario comentario =
        comentarioRepositorio
            .buscarParaModificar(comentarioId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "comentario-no-encontrado",
                        "Comentario no encontrado",
                        "El comentario solicitado no existe."));

    if (comentario.getHilo().getInspeccionId() != null
        || !comentario.getAutorId().equals(autorId)
        || comentario.getOcultoEn() != null
        || comentario.getEliminadoEn() != null) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "comentario-no-encontrado",
          "Comentario no encontrado",
          "El comentario solicitado no existe.");
    }

    Instant ahora = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Instant limite = comentario.getCreadoEn().plus(15, ChronoUnit.MINUTES);
    if (!ahora.isBefore(limite)) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "plazo-edicion-vencido",
          "Plazo de edición vencido",
          "El comentario solo puede editarse dentro de los 15 minutos posteriores a su publicación.");
    }

    String textoAnterior = comentario.getTexto();
    comentario.setTexto(dto.texto().trim());
    comentario.setEditadoEn(ahora);

    Comentario guardado = comentarioRepositorio.saveAndFlush(comentario);
    entityManager.refresh(guardado);
    notificarMenciones(comentario.getHilo(), autorId, guardado.getTexto(), textoAnterior);
    return conAutor(guardado);
  }

  public void eliminarComentario(UUID comentarioId, UUID autorId) {
    Comentario comentario =
        comentarioRepositorio
            .buscarParaModificar(comentarioId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "comentario-no-encontrado",
                        "Comentario no encontrado",
                        "El comentario solicitado no existe."));

    if (comentario.getHilo().getInspeccionId() != null
        || !comentario.getAutorId().equals(autorId)
        || comentario.getOcultoEn() != null) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "comentario-no-encontrado",
          "Comentario no encontrado",
          "El comentario solicitado no existe.");
    }

    if (comentario.getEliminadoEn() != null) {
      return;
    }

    Instant ahora = clock.instant().truncatedTo(ChronoUnit.MICROS);
    comentario.setEliminadoEn(ahora);
    comentarioRepositorio.saveAndFlush(comentario);
  }

  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.MODERAR,
      entidad = "comentario",
      entidadId = "#comentarioId",
      motivo = "#motivo",
      condicion = "#result.modificado",
      valorAnterior = "#result.valoresAnteriores('oculto')",
      valorNuevo = "#result.valoresNuevos('oculto')")
  public ResultadoModeracion ocultarComentario(UUID comentarioId, String motivo) {
    if (motivo == null || motivo.isBlank()) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "motivo-vacio",
          "Motivo inválido",
          "El motivo de la moderación no puede estar vacío.");
    }

    Comentario comentario =
        comentarioRepositorio
            .buscarParaModificar(comentarioId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "comentario-no-encontrado",
                        "Comentario no encontrado",
                        "El comentario solicitado no existe."));

    if (comentario.getHilo().getInspeccionId() != null) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "comentario-no-encontrado",
          "Comentario no encontrado",
          "El comentario solicitado no existe.");
    }

    if (comentario.getOcultoEn() != null) {
      return new ResultadoModeracion(false, comentarioId, null, null, List.of());
    }

    Instant ahora = clock.instant().truncatedTo(ChronoUnit.MICROS);
    String motivoLimpio = motivo.trim();

    comentario.setOcultoEn(ahora);
    comentario.setMotivoOcultamiento(motivoLimpio);

    List<UUID> idsRecienOcultados = new ArrayList<>();
    idsRecienOcultados.add(comentarioId);

    if (comentario.getRespuestaA() == null) {
      List<Comentario> respuestas =
          comentarioRepositorio.buscarRespuestasDirectasParaModificar(
              comentarioId, comentario.getHilo().getId());
      for (Comentario respuesta : respuestas) {
        if (respuesta.getOcultoEn() == null) {
          respuesta.setOcultoEn(ahora);
          respuesta.setMotivoOcultamiento(motivoLimpio);
          idsRecienOcultados.add(respuesta.getId());
        }
      }
      comentarioRepositorio.saveAllAndFlush(respuestas);
    }

    comentarioRepositorio.saveAndFlush(comentario);
    return new ResultadoModeracion(true, comentarioId, ahora, motivoLimpio, idsRecienOcultados);
  }

  @PreAuthorize("hasAnyRole('CATEDRATICO','ADMINISTRADOR')")
  @Auditable(
      accion = Acciones.MODERAR,
      entidad = "hilo",
      entidadId = "#hiloId",
      motivo = "#motivo",
      condicion = "#result.modificado",
      valorAnterior = "#result.valoresAnteriores('cerrado')",
      valorNuevo = "#result.valoresNuevos('cerrado')")
  public ResultadoModeracion cerrarHilo(UUID hiloId, String motivo) {
    if (motivo == null || motivo.isBlank()) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "motivo-vacio",
          "Motivo inválido",
          "El motivo de la moderación no puede estar vacío.");
    }

    Hilo hilo =
        hiloRepositorio
            .buscarParaModificar(hiloId)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.NOT_FOUND,
                        "hilo-no-encontrado",
                        "Hilo no encontrado",
                        "El hilo solicitado no existe o no pertenece al foro permanente."));

    if (hilo.getCerradoEn() != null) {
      return new ResultadoModeracion(false, hiloId, null, null, List.of());
    }

    Instant ahora = clock.instant().truncatedTo(ChronoUnit.MICROS);
    String motivoLimpio = motivo.trim();

    hilo.setCerradoEn(ahora);
    hilo.setMotivoCierre(motivoLimpio);

    hiloRepositorio.saveAndFlush(hilo);
    return new ResultadoModeracion(true, hiloId, ahora, motivoLimpio, List.of(hiloId));
  }

  private void notificarAlertaPuenteMalo(UUID puenteId, Hilo hilo) {
    if (hilo.getCategoria() != CategoriaHilo.ALERTA) {
      return;
    }

    String estadoActual = hiloRepositorio.obtenerEstadoActualPuente(puenteId).orElse(null);
    if (!"MALO".equals(estadoActual)) {
      return;
    }

    List<UUID> destinatarios =
        hiloRepositorio.buscarDestinatariosAlertaPuente().stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (destinatarios.isEmpty()) {
      return;
    }

    String nombrePuente = hiloRepositorio.obtenerNombrePuente(puenteId).orElse("Puente");
    String mensaje = "Alerta en puente " + nombrePuente + " (estado MALO): " + hilo.getTitulo();
    String enlace = "/puentes/" + puenteId + "/foro/" + hilo.getId();

    for (UUID destinatarioId : destinatarios) {
      notificacionService.notificar(
          destinatarioId, TIPO_NOTIFICACION_ALERTA_PUENTE, mensaje, enlace);
    }
  }

  // RN-FOR-10: Menciones @usuario y @{uuid} en comentarios y respuestas
  private void notificarMenciones(
      Hilo hilo, UUID autorId, String textoNuevo, String textoAnterior) {
    Set<UUID> candidatos = extraerMenciones(textoNuevo);
    candidatos.remove(autorId);
    if (textoAnterior != null) {
      candidatos.removeAll(extraerMenciones(textoAnterior));
    }
    if (candidatos.isEmpty()) {
      return;
    }

    Set<UUID> activos =
        new HashSet<>(hiloRepositorio.filtrarUsuariosActivos(new ArrayList<>(candidatos)));
    if (activos.isEmpty()) {
      return;
    }

    String enlace =
        hilo.getInspeccionId() == null
            ? "/puentes/" + hilo.getPuenteId() + "/foro/" + hilo.getId()
            : "/inspecciones/" + hilo.getInspeccionId() + "/foro";

    for (UUID mencionadoId : candidatos) {
      if (activos.contains(mencionadoId)) {
        notificacionService.notificar(
            mencionadoId, TIPO_NOTIFICACION_MENCION, MENSAJE_MENCION_FORO, enlace);
      }
    }
  }

  static Set<UUID> extraerMenciones(String texto) {
    Set<UUID> menciones = new LinkedHashSet<>();
    if (texto == null || texto.isBlank()) {
      return menciones;
    }
    Matcher matcher = PATRON_MENCION.matcher(texto);
    while (matcher.find()) {
      menciones.add(UUID.fromString(matcher.group(1)));
    }
    return menciones;
  }

  private static void validarEventoInspeccionPublicada(InspeccionPublicada evento) {
    if (evento == null
        || evento.inspeccionId() == null
        || evento.puenteId() == null
        || evento.indiceCondicion() == null
        || evento.estado() == null
        || evento.fecha() == null) {
      throw new IllegalArgumentException(
          "El evento InspeccionPublicada contiene campos nulos no permitidos.");
    }
  }

  private Map<UUID, String> nombresAutores(Stream<UUID> autores) {
    List<UUID> ids = autores.filter(Objects::nonNull).distinct().toList();
    if (ids.isEmpty()) {
      return new HashMap<>();
    }
    return hiloRepositorio.buscarNombresAutores(ids).stream()
        .collect(Collectors.toMap(HiloRepository.Autor::getId, HiloRepository.Autor::getNombre));
  }

  private HiloDTO conAutor(Hilo hilo) {
    Map<UUID, String> autores = nombresAutores(Stream.of(hilo.getAutorId()));
    return mapper.aDTO(hilo).conAutorNombre(autores.get(hilo.getAutorId()));
  }

  private ComentarioDTO conAutor(Comentario comentario) {
    Map<UUID, String> autores = nombresAutores(Stream.of(comentario.getAutorId()));
    return mapper.aDTO(comentario).conAutorNombre(autores.get(comentario.getAutorId()));
  }

  private static void validarPaginacion(int page, int size) {
    if (page < 0 || size <= 0 || size > MAX_TAMANO_PAGINA) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida",
          "Paginación inválida",
          "Los parámetros de paginación deben cumplir page >= 0 y 1 <= size <= 100.");
    }

    if ((long) page * (long) size > MAX_OFFSET_SOPORTADO) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida",
          "Paginación inválida",
          "El desplazamiento solicitado supera el límite máximo soportado por el sistema.");
    }
  }
}
