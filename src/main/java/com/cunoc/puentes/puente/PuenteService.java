package com.cunoc.puentes.puente;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import com.cunoc.puentes.puente.dto.CrearPuenteDTO;
import com.cunoc.puentes.puente.dto.DarDeBajaPuenteDTO;
import com.cunoc.puentes.puente.dto.EditarPuenteDTO;
import com.cunoc.puentes.puente.dto.PaginaPuentesDTO;
import com.cunoc.puentes.puente.dto.PuenteCercanoDTO;
import com.cunoc.puentes.puente.dto.PuenteDTO;
import java.util.List;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inventario de puentes (RN-INV-01 a RN-INV-06, RN-INV-10). */
@Service
@Transactional
public class PuenteService {

  static final int MAX_TAMANO_PAGINA = 100;
  static final int MAX_LONGITUD_TEXTO = 120;
  private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

  private final PuenteRepository puentes;
  private final MunicipioRepository municipios;
  private final GeneradorCodigoPuente generador;
  private final PuentesCercanos cercanos;
  private final PuenteMapper mapper;

  PuenteService(
      PuenteRepository puentes,
      MunicipioRepository municipios,
      GeneradorCodigoPuente generador,
      PuentesCercanos cercanos,
      PuenteMapper mapper) {
    this.puentes = puentes;
    this.municipios = municipios;
    this.generador = generador;
    this.cercanos = cercanos;
    this.mapper = mapper;
  }

  @Auditable(accion = Acciones.CREAR, entidad = "puente", entidadId = "#result.id")
  public PuenteDTO crear(CrearPuenteDTO datos) {
    Municipio municipio = municipioCongruente(datos.departamentoCodigo(), datos.municipioCodigo());
    advertirDuplicados(datos.latitud(), datos.longitud(), null, datos.confirmado());
    Puente puente = new Puente();
    puente.setCodigo(generador.siguiente(municipio));
    puente.setMunicipio(municipio);
    puente.setNombre(datos.nombre().strip());
    puente.setRuta(datos.ruta().strip());
    puente.setUbicacion(punto(datos.latitud(), datos.longitud()));
    puente.setAnioConstruccion(datos.anioConstruccion());
    return mapper.aDTO(puentes.save(puente));
  }

  @Auditable(accion = Acciones.MODIFICAR, entidad = "puente", entidadId = "#id")
  public PuenteDTO editar(UUID id, EditarPuenteDTO datos) {
    Puente puente = existente(id, true);
    if (!puente.isActivo()) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "puente-dado-de-baja",
          "Puente dado de baja",
          "Un puente dado de baja no se puede editar.");
    }
    advertirDuplicados(datos.latitud(), datos.longitud(), id, datos.confirmado());
    puente.setNombre(datos.nombre().strip());
    puente.setRuta(datos.ruta().strip());
    puente.setUbicacion(punto(datos.latitud(), datos.longitud()));
    puente.setAnioConstruccion(datos.anioConstruccion());
    return mapper.aDTO(puente);
  }

  /** RN-INV-08: baja lógica con motivo; nunca se borra la fila (DT-BD-11). */
  @Auditable(
      accion = Acciones.DAR_DE_BAJA,
      entidad = "puente",
      entidadId = "#id",
      motivo = "#datos.motivo()")
  public PuenteDTO darDeBaja(UUID id, DarDeBajaPuenteDTO datos) {
    Puente puente = existente(id, true);
    if (!puente.isActivo()) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "puente-dado-de-baja",
          "Puente dado de baja",
          "El puente ya estaba dado de baja.");
    }
    puente.setActivo(false);
    puente.setMotivoBaja(datos.motivo().strip());
    return mapper.aDTO(puente);
  }

  /** El público solo ve puentes activos; las bajas, solo el Administrador. */
  @Transactional(readOnly = true)
  public PuenteDTO detalle(UUID id, boolean verBajas) {
    return mapper.aDTO(existente(id, verBajas));
  }

  @Transactional(readOnly = true)
  public PaginaPuentesDTO listar(
      String departamento,
      String municipio,
      EstadoFiltro estado,
      String texto,
      boolean incluirBajas,
      int pagina,
      int tamano) {
    if (pagina < 0 || tamano < 1 || tamano > MAX_TAMANO_PAGINA) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida",
          "Paginación inválida",
          "Los parámetros de paginación deben cumplir page >= 0 y 1 <= size <= 100.");
    }
    if (texto != null && texto.length() > MAX_LONGITUD_TEXTO) {
      throw new NegocioException(
          HttpStatus.BAD_REQUEST,
          "busqueda-muy-larga",
          "Búsqueda demasiado larga",
          "El texto de búsqueda no puede superar los 120 caracteres.");
    }
    Page<Puente> resultado =
        puentes.findAll(
            FiltrosPuente.de(departamento, municipio, estado, texto, incluirBajas),
            PageRequest.of(pagina, tamano, Sort.by("codigo")));
    return new PaginaPuentesDTO(
        resultado.getContent().stream().map(mapper::aDTO).toList(),
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements(),
        resultado.getTotalPages());
  }

  @Transactional(readOnly = true)
  public List<PuenteCercanoDTO> cercanos(double latitud, double longitud, UUID excluir) {
    return cercanos.buscar(latitud, longitud, excluir);
  }

  /** Para la aprobación de solicitudes: mismo alta, misma validación. */
  Municipio municipioCongruente(String departamentoCodigo, String municipioCodigo) {
    Municipio municipio =
        municipios
            .findById(municipioCodigo)
            .orElseThrow(
                () ->
                    new NegocioException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "municipio-no-encontrado",
                        "Municipio no encontrado",
                        "El municipio no existe en el catálogo del INE."));
    // RN-INV-04: el municipio debe pertenecer al departamento elegido
    if (!municipio.getDepartamento().getCodigo().equals(departamentoCodigo)) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "municipio-incongruente",
          "Municipio incongruente",
          "El municipio no pertenece al departamento indicado.");
    }
    return municipio;
  }

  /** RN-INV-06: advierte (409 con la lista) pero no bloquea si el usuario ya confirmó. */
  void advertirDuplicados(double latitud, double longitud, UUID excluir, boolean confirmado) {
    if (confirmado) {
      return;
    }
    List<PuenteCercanoDTO> encontrados = cercanos.buscar(latitud, longitud, excluir);
    if (!encontrados.isEmpty()) {
      NegocioException advertencia =
          new NegocioException(
              HttpStatus.CONFLICT,
              "posible-duplicado",
              "Posible puente duplicado",
              "Hay %d puente(s) a menos de 100 m. Confirme para continuar."
                  .formatted(encontrados.size()));
      advertencia.getBody().setProperty("cercanos", encontrados);
      throw advertencia;
    }
  }

  static Point punto(double latitud, double longitud) {
    return WGS84.createPoint(new Coordinate(longitud, latitud));
  }

  private Puente existente(UUID id, boolean verBajas) {
    return puentes
        .findConCatalogoById(id)
        .filter(puente -> verBajas || puente.isActivo())
        .orElseThrow(
            () ->
                new NegocioException(
                    HttpStatus.NOT_FOUND,
                    "puente-no-encontrado",
                    "Puente no encontrado",
                    "No existe un puente con el id indicado."));
  }
}
