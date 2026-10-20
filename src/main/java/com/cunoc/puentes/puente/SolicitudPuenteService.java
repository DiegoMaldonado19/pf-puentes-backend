package com.cunoc.puentes.puente;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.puente.dto.AprobarSolicitudDTO;
import com.cunoc.puentes.puente.dto.CrearPuenteDTO;
import com.cunoc.puentes.puente.dto.CrearSolicitudPuenteDTO;
import com.cunoc.puentes.puente.dto.PaginaSolicitudesDTO;
import com.cunoc.puentes.puente.dto.PuenteDTO;
import com.cunoc.puentes.puente.dto.RechazarSolicitudDTO;
import com.cunoc.puentes.puente.dto.SolicitudPuenteDTO;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RN-INV-07: el Catedrático solicita, el Administrador aprueba (crea el puente) o rechaza. */
@Service
@Transactional
public class SolicitudPuenteService {

  private final SolicitudPuenteRepository solicitudes;
  private final PuenteRepository puentes;
  private final PuenteService puenteService;
  private final SolicitudPuenteMapper mapper;

  SolicitudPuenteService(
      SolicitudPuenteRepository solicitudes,
      PuenteRepository puentes,
      PuenteService puenteService,
      SolicitudPuenteMapper mapper) {
    this.solicitudes = solicitudes;
    this.puentes = puentes;
    this.puenteService = puenteService;
    this.mapper = mapper;
  }

  @Auditable(accion = Acciones.CREAR, entidad = "solicitud_puente", entidadId = "#result.id")
  public SolicitudPuenteDTO solicitar(UUID catedraticoId, CrearSolicitudPuenteDTO datos) {
    Municipio municipio =
        puenteService.municipioCongruente(datos.departamentoCodigo(), datos.municipioCodigo());
    puenteService.advertirDuplicados(datos.latitud(), datos.longitud(), null, datos.confirmado());
    SolicitudPuente solicitud = new SolicitudPuente();
    solicitud.setCatedraticoId(catedraticoId);
    solicitud.setNombre(datos.nombre().strip());
    solicitud.setMunicipio(municipio);
    solicitud.setRuta(datos.ruta().strip());
    solicitud.setUbicacion(PuenteService.punto(datos.latitud(), datos.longitud()));
    return mapper.aDTO(solicitudes.save(solicitud));
  }

  /** El Administrador ve todas; el Catedrático, solo las suyas. */
  @Transactional(readOnly = true)
  public PaginaSolicitudesDTO listar(
      UUID usuarioId, boolean esAdministrador, EstadoSolicitud estado, int pagina, int tamano) {
    if (pagina < 0 || tamano < 1 || tamano > PuenteService.MAX_TAMANO_PAGINA) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida",
          "Paginación inválida",
          "Los parámetros de paginación deben cumplir page >= 0 y 1 <= size <= 100.");
    }
    Specification<SolicitudPuente> filtro = Specification.unrestricted();
    if (!esAdministrador) {
      filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("catedraticoId"), usuarioId));
    }
    if (estado != null) {
      filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("estado"), estado));
    }
    Page<SolicitudPuente> resultado =
        solicitudes.findAll(
            filtro,
            PageRequest.of(
                pagina, tamano, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id"))));
    return new PaginaSolicitudesDTO(
        resultado.getContent().stream().map(mapper::aDTO).toList(),
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  /** La de otro Catedrático responde 404: no puede saber que existe. */
  @Transactional(readOnly = true)
  public SolicitudPuenteDTO detalle(UUID id, UUID usuarioId, boolean esAdministrador) {
    return mapper.aDTO(
        solicitudes
            .findConCatalogoById(id)
            .filter(s -> esAdministrador || s.getCatedraticoId().equals(usuarioId))
            .orElseThrow(SolicitudPuenteService::noEncontrada));
  }

  /** Crea el puente con la misma validación y el mismo código que un alta directa. */
  @Auditable(accion = Acciones.CAMBIAR_ESTADO, entidad = "solicitud_puente", entidadId = "#id")
  public SolicitudPuenteDTO aprobar(UUID id, UUID administradorId, AprobarSolicitudDTO datos) {
    SolicitudPuente solicitud = pendiente(id);
    PuenteDTO puente =
        puenteService.crear(
            new CrearPuenteDTO(
                solicitud.getNombre(),
                solicitud.getMunicipio().getDepartamento().getCodigo(),
                solicitud.getMunicipio().getCodigo(),
                solicitud.getRuta(),
                solicitud.getUbicacion().getY(),
                solicitud.getUbicacion().getX(),
                null,
                datos.confirmado()));
    solicitud.setEstado(EstadoSolicitud.APROBADA);
    solicitud.setAdministradorId(administradorId);
    solicitud.setPuente(puentes.getReferenceById(puente.id()));
    return mapper.aDTO(solicitud);
  }

  @Auditable(
      accion = Acciones.CAMBIAR_ESTADO,
      entidad = "solicitud_puente",
      entidadId = "#id",
      motivo = "#datos.motivo()")
  public SolicitudPuenteDTO rechazar(UUID id, UUID administradorId, RechazarSolicitudDTO datos) {
    SolicitudPuente solicitud = pendiente(id);
    solicitud.setEstado(EstadoSolicitud.RECHAZADA);
    solicitud.setAdministradorId(administradorId);
    solicitud.setMotivoRechazo(datos.motivo().strip());
    return mapper.aDTO(solicitud);
  }

  private SolicitudPuente pendiente(UUID id) {
    SolicitudPuente solicitud =
        solicitudes.bloquear(id).orElseThrow(SolicitudPuenteService::noEncontrada);
    if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "solicitud-ya-resuelta",
          "Solicitud ya resuelta",
          "La solicitud ya fue %s."
              .formatted(solicitud.getEstado().name().toLowerCase(Locale.ROOT)));
    }
    return solicitud;
  }

  private static NegocioException noEncontrada() {
    return new NegocioException(
        HttpStatus.NOT_FOUND,
        "solicitud-no-encontrada",
        "Solicitud no encontrada",
        "No existe una solicitud de alta con el id indicado.");
  }
}
