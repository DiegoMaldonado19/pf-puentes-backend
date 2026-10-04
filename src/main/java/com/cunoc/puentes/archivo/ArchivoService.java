package com.cunoc.puentes.archivo;

import com.cunoc.puentes.archivo.ArchivoRepository.InspeccionDelArchivo;
import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import com.cunoc.puentes.archivo.dto.SubirDocumentoDTO;
import com.cunoc.puentes.archivo.dto.SubirFotoDTO;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.EstadoInspeccion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArchivoService {

  // RN-ARC-04
  static final long MAX_BYTES_PDF = 20L * 1024 * 1024;
  static final int MAX_FOTOS_POR_INSPECCION = 60;
  static final int MAX_DOCUMENTOS_POR_INSPECCION = 10;
  // RN-ARC-06
  static final int DIAS_PARA_PURGAR = 210;

  private static final Logger log = LoggerFactory.getLogger(ArchivoService.class);
  private static final Set<TipoArchivo> FOTOS = EnumSet.of(TipoArchivo.JPEG, TipoArchivo.WEBP);
  private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

  private final AlmacenamientoArchivos almacenamiento;
  private final ArchivoRepository repositorio;
  private final ArchivoMapper mapper;

  ArchivoService(
      AlmacenamientoArchivos almacenamiento, ArchivoRepository repositorio, ArchivoMapper mapper) {
    this.almacenamiento = almacenamiento;
    this.repositorio = repositorio;
    this.mapper = mapper;
  }

  /** DT-ALM-04: la foto comprimida y su miniatura de 300 px llegan ya generadas por el cliente. */
  public ArchivoDTO guardarFoto(UUID inspeccionId, UUID usuarioId, SubirFotoDTO datos) {
    return subir(
        inspeccionId,
        usuarioId,
        datos.id(),
        puenteId -> {
          byte[] foto = bytes(datos.foto());
          byte[] miniatura = bytes(datos.miniatura());
          TipoArchivo tipoFoto = tipoDeFoto(foto);
          TipoArchivo tipoMiniatura = tipoDeFoto(miniatura);
          exigirCupo(inspeccionId, FOTOS, MAX_FOTOS_POR_INSPECCION, "fotos");
          String nombre = carpeta(puenteId, inspeccionId) + UUID.randomUUID();
          Archivo archivo =
              nuevo(
                  datos.id(), inspeccionId, tipoFoto, guardar(nombre, foto, tipoFoto), foto.length);
          archivo.setClaveMiniatura(guardar(nombre + "-miniatura", miniatura, tipoMiniatura));
          archivo.setElementoRef(datos.elementoRef());
          archivo.setUbicacion(punto(datos.latitud(), datos.longitud()));
          archivo.setCapturadaEn(datos.capturadaEn());
          return archivo;
        });
  }

  public ArchivoDTO guardarDocumento(UUID inspeccionId, UUID usuarioId, SubirDocumentoDTO datos) {
    return subir(
        inspeccionId,
        usuarioId,
        datos.id(),
        puenteId -> {
          byte[] pdf = bytes(datos.pdf());
          if (TipoArchivo.detectar(pdf).filter(TipoArchivo.PDF::equals).isEmpty()) {
            throw invalido("El documento debe ser PDF.");
          }
          if (pdf.length > MAX_BYTES_PDF) {
            throw invalido("El PDF pasa de 20 MB.");
          }
          exigirCupo(
              inspeccionId,
              EnumSet.of(TipoArchivo.PDF),
              MAX_DOCUMENTOS_POR_INSPECCION,
              "documentos");
          String nombre = carpeta(puenteId, inspeccionId) + UUID.randomUUID();
          return nuevo(
              datos.id(),
              inspeccionId,
              TipoArchivo.PDF,
              guardar(nombre, pdf, TipoArchivo.PDF),
              pdf.length);
        });
  }

  /** RN-ARC-05: solo en un borrador propio; la evidencia de una publicada no se borra. */
  public void eliminar(UUID archivoId, UUID usuarioId) {
    Archivo archivo =
        repositorio
            .findById(archivoId)
            // La evidencia de una orden (RN-MTO-05) no se borra por aquí
            .filter(encontrado -> encontrado.getInspeccionId() != null)
            .orElseThrow(
                () ->
                    noEncontrado(
                        "archivo-no-encontrado",
                        "El archivo no existe",
                        "No hay un archivo con ese id."));
    exigirBorrador(inspeccionPropia(archivo.getInspeccionId(), usuarioId));
    repositorio.delete(archivo);
    eliminarObjetos(archivo);
  }

  /**
   * Para el detalle de la inspección (P5) y la revisión (P7): quien llama ya autorizó la lectura.
   */
  public List<ArchivoDTO> listar(UUID inspeccionId) {
    return repositorio.findByInspeccionIdOrderByCreadoEn(inspeccionId).stream()
        .map(this::aDTO)
        .toList();
  }

  /** RN-ARC-06: borra los objetos de los borradores abandonados y conserva sus registros. */
  @Scheduled(cron = "0 0 3 * * *", zone = "America/Guatemala")
  @SchedulerLock(name = "purga-de-archivos")
  public void purgarBorradoresAbandonados() {
    List<Archivo> vencidos =
        repositorio.buscarParaPurgar(Instant.now().minus(DIAS_PARA_PURGAR, ChronoUnit.DAYS));
    int purgados = 0;
    for (Archivo archivo : vencidos) {
      try {
        eliminarObjetos(archivo);
        archivo.setPurgadoEn(Instant.now());
        repositorio.save(archivo);
        purgados++;
      } catch (RuntimeException ex) {
        // Sin purgado_en: la próxima corrida lo vuelve a intentar
        log.error("RN-ARC-06: no se pudo purgar el archivo {}", archivo.getId(), ex);
      }
    }
    log.info("RN-ARC-06: {} de {} archivos purgados", purgados, vencidos.size());
  }

  // DT-OFF-07: reenviar un id ya guardado devuelve ese archivo, sin guardar nada otra vez
  private ArchivoDTO subir(
      UUID inspeccionId, UUID usuarioId, UUID id, Function<UUID, Archivo> guardarObjetos) {
    InspeccionDelArchivo inspeccion = inspeccionPropia(inspeccionId, usuarioId);
    Optional<Archivo> existente = repositorio.findById(id);
    if (existente.isPresent()) {
      if (!inspeccionId.equals(existente.get().getInspeccionId())) {
        throw new NegocioException(
            HttpStatus.CONFLICT,
            "id-en-uso",
            "El id ya está en uso",
            "Ese id pertenece a un archivo de otra inspección.");
      }
      return aDTO(existente.get());
    }
    exigirBorrador(inspeccion);
    // ponytail: si la BD falla después de MinIO (o MinIO después de borrar la fila, en eliminar),
    // quedan objetos huérfanos; barrerlos si llega a importar
    return aDTO(repositorio.save(guardarObjetos.apply(inspeccion.getPuenteId())));
  }

  // DT-SEC-04 y Backend/02: la inspección de otro autor no existe para quien pregunta
  private InspeccionDelArchivo inspeccionPropia(UUID inspeccionId, UUID usuarioId) {
    return repositorio
        .buscarInspeccion(inspeccionId)
        .filter(inspeccion -> inspeccion.getAutorId().equals(usuarioId))
        .orElseThrow(
            () ->
                noEncontrado(
                    "inspeccion-no-encontrada",
                    "La inspección no existe",
                    "No tienes una inspección con ese id."));
  }

  // RN-INS-15 y RN-ARC-05
  private static void exigirBorrador(InspeccionDelArchivo inspeccion) {
    if (inspeccion.getEstado() != EstadoInspeccion.BORRADOR) {
      throw new NegocioException(
          HttpStatus.CONFLICT,
          "inspeccion-no-editable",
          "La inspección ya no se puede modificar",
          "Solo se agregan o quitan archivos mientras la inspección es un borrador.");
    }
  }

  // ponytail: contar sin bloquear deja pasar dos subidas simultáneas sobre el tope; P6 sube de una
  // en una (RN-OFF-05)
  private void exigirCupo(UUID inspeccionId, Set<TipoArchivo> tipos, int maximo, String nombre) {
    if (repositorio.countByInspeccionIdAndTipoIn(inspeccionId, tipos) >= maximo) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "limite-de-archivos",
          "La inspección llegó al límite de archivos",
          "Una inspección admite hasta %d %s.".formatted(maximo, nombre));
    }
  }

  private String guardar(String nombre, byte[] contenido, TipoArchivo tipo) {
    String clave = nombre + "." + tipo.extension;
    almacenamiento.guardar(clave, contenido, tipo.mime);
    return clave;
  }

  private void eliminarObjetos(Archivo archivo) {
    almacenamiento.eliminar(archivo.getClave());
    if (archivo.getClaveMiniatura() != null) {
      almacenamiento.eliminar(archivo.getClaveMiniatura());
    }
  }

  private ArchivoDTO aDTO(Archivo archivo) {
    return mapper.aDTO(
        archivo,
        urlFirmada(archivo, archivo.getClave()),
        urlFirmada(archivo, archivo.getClaveMiniatura()));
  }

  // RN-ARC-06: lo purgado ya no tiene objeto que firmar
  private String urlFirmada(Archivo archivo, String clave) {
    return clave == null || archivo.getPurgadoEn() != null
        ? null
        : almacenamiento.obtenerUrlFirmada(clave);
  }

  private static Archivo nuevo(
      UUID id, UUID inspeccionId, TipoArchivo tipo, String clave, long tamano) {
    Archivo archivo = new Archivo();
    archivo.setId(id);
    archivo.setInspeccionId(inspeccionId);
    archivo.setTipo(tipo);
    archivo.setClave(clave);
    archivo.setTamano(tamano);
    return archivo;
  }

  private static Point punto(Double latitud, Double longitud) {
    return latitud == null || longitud == null
        ? null
        : WGS84.createPoint(new Coordinate(longitud, latitud));
  }

  private static byte[] bytes(MultipartFile archivo) {
    try {
      return archivo.getBytes();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  // DT-ALM-02 + DT-SEC-08: {año}/{mes}/{puente_id}/{inspeccion_id}/, nunca el nombre del cliente
  private static String carpeta(UUID puenteId, UUID inspeccionId) {
    YearMonth mes = YearMonth.now(ZoneOffset.UTC);
    return "%d/%02d/%s/%s/".formatted(mes.getYear(), mes.getMonthValue(), puenteId, inspeccionId);
  }

  private static TipoArchivo tipoDeFoto(byte[] contenido) {
    return TipoArchivo.detectar(contenido)
        .filter(FOTOS::contains)
        .orElseThrow(() -> invalido("La foto debe ser WebP o JPEG."));
  }

  private static NegocioException invalido(String detalle) {
    return new NegocioException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "archivo-invalido",
        "El archivo no se puede guardar",
        detalle);
  }

  private static NegocioException noEncontrado(String tipo, String titulo, String detalle) {
    return new NegocioException(HttpStatus.NOT_FOUND, tipo, titulo, detalle);
  }
}
