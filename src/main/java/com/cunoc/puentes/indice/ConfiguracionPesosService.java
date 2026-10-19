package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.auditoria.Acciones;
import com.cunoc.puentes.common.auditoria.Auditable;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crea juegos nuevos; no expone actualización o borrado de configuraciones históricas. */
@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ConfiguracionPesosService {

  private final VersionPesosRepository versiones;
  private final CreacionPesoService creador;
  private final PesosMapper mapper;

  ConfiguracionPesosService(
      VersionPesosRepository versiones, CreacionPesoService creador, PesosMapper mapper) {
    this.versiones = versiones;
    this.creador = creador;
    this.mapper = mapper;
  }

  /** Los elementos permitidos deben proceder del servidor, nunca del cuerpo de una petición. */
  @Transactional
  @Auditable(accion = Acciones.CREAR, entidad = "version_pesos", entidadId = "#result")
  public UUID crear(JuegoPesos juego, ElementosDeDano elementos) {
    if (juego == null || elementos == null) {
      throw new IllegalArgumentException("El juego y los metadatos de daño son obligatorios.");
    }
    if (!juego.formularioVersionId().equals(elementos.formularioVersionId())) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "metadatos-de-otra-version",
          "Metadatos incompatibles",
          "Los campos de daño deben corresponder a la versión del formulario del juego.");
    }
    if (!versiones.existeFormulario(juego.formularioVersionId())) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "formulario-no-encontrado",
          "Formulario no encontrado",
          "La versión del formulario indicada no existe.");
    }
    for (PesoConfigurado peso : juego.pesos()) {
      if (!elementos.referencias().contains(peso.elementoRef())) {
        throw new NegocioException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "elemento-de-dano-invalido",
            "Elemento de daño inválido",
            "Todas las referencias del juego deben identificar campos de daño de su formulario.");
      }
    }
    VersionPesos version = versiones.save(mapper.aVersion(juego));
    for (PesoConfigurado peso : juego.pesos()) {
      creador.crear(peso, version);
    }
    return version.getId();
  }
}
