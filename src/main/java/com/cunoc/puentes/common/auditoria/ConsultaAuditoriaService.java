package com.cunoc.puentes.common.auditoria;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.dto.PaginaAuditoriaDTO;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * RN-USR-09: el Administrador consulta la bitácora. Solo lectura; los valores ya salieron saneados
 * al escribirse.
 */
@Service
public class ConsultaAuditoriaService {

  private final AuditoriaRepository repositorio;
  private final AuditoriaMapper mapper;

  ConsultaAuditoriaService(AuditoriaRepository repositorio, AuditoriaMapper mapper) {
    this.repositorio = repositorio;
    this.mapper = mapper;
  }

  @PreAuthorize("hasRole('ADMINISTRADOR')")
  @Transactional(readOnly = true)
  public PaginaAuditoriaDTO listar(
      String entidad,
      UUID entidadId,
      UUID usuarioId,
      String accion,
      Instant desde,
      Instant hasta,
      Pageable pagina) {
    if (desde != null && hasta != null && desde.isAfter(hasta)) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "rango-invalido",
          "Rango de fechas inválido",
          "La fecha inicial no puede ser posterior a la final.");
    }
    return PaginaAuditoriaDTO.de(
        repositorio
            .findAll(filtro(entidad, entidadId, usuarioId, accion, desde, hasta), pagina(pagina))
            .map(mapper::aDTO));
  }

  private static Specification<Auditoria> filtro(
      String entidad, UUID entidadId, UUID usuarioId, String accion, Instant desde, Instant hasta) {
    Specification<Auditoria> spec = Specification.unrestricted();
    if (StringUtils.hasText(entidad)) {
      spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("entidad"), entidad));
    }
    if (entidadId != null) {
      spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("entidadId"), entidadId));
    }
    if (usuarioId != null) {
      spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("usuarioId"), usuarioId));
    }
    if (StringUtils.hasText(accion)) {
      spec = spec.and((raiz, consulta, cb) -> cb.equal(raiz.get("accion"), accion));
    }
    if (desde != null) {
      spec = spec.and((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("creadoEn"), desde));
    }
    if (hasta != null) {
      spec = spec.and((raiz, consulta, cb) -> cb.lessThanOrEqualTo(raiz.get("creadoEn"), hasta));
    }
    return spec;
  }

  private static Pageable pagina(Pageable pagina) {
    return PageRequest.of(
        pagina.getPageNumber(),
        Math.min(pagina.getPageSize(), 100),
        Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));
  }
}
