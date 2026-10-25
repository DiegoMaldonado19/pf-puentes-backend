package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.dto.CrearJuegoPesosDTO;
import com.cunoc.puentes.indice.dto.JuegoPesosDTO;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crea un juego completo desde metadatos confiables, sin cambiar resultados publicados. */
@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class CreacionJuegoPesosService {
  private final JdbcTemplate jdbc;
  private final MetadatosDanoFormulario metadatos;
  private final PesosMapper mapper;
  private final ConfiguracionPesosService configuracion;
  private final ConsultaPesosService consulta;

  CreacionJuegoPesosService(
      JdbcTemplate jdbc,
      MetadatosDanoFormulario metadatos,
      PesosMapper mapper,
      ConfiguracionPesosService configuracion,
      ConsultaPesosService consulta) {
    this.jdbc = jdbc;
    this.metadatos = metadatos;
    this.mapper = mapper;
    this.configuracion = configuracion;
    this.consulta = consulta;
  }

  @Transactional
  public JuegoPesosDTO crear(UUID formularioId, CrearJuegoPesosDTO datos) {
    if (formularioId == null || datos == null) throw invalido();
    if (jdbc.query(
            "SELECT id FROM formulario_version WHERE id = ? FOR SHARE",
            (rs, fila) -> rs.getObject(1, UUID.class),
            formularioId)
        .isEmpty()) {
      throw new NegocioException(
          HttpStatus.NOT_FOUND,
          "formulario-no-encontrado",
          "Formulario no encontrado",
          "La versión del formulario indicada no existe.");
    }
    JuegoPesos juego;
    try {
      juego = mapper.aJuego(formularioId, datos);
    } catch (IllegalArgumentException error) {
      throw invalido();
    }
    var elementos = metadatos.obtener(formularioId);
    var recibidos =
        juego.pesos().stream().map(PesoConfigurado::elementoRef).collect(Collectors.toSet());
    if (!recibidos.equals(elementos.referencias())) {
      throw new NegocioException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "juego-pesos-incompleto",
          "Referencias de pesos incompatibles",
          "El juego debe contener exactamente un peso para cada campo de daño de esta versión. Actualiza los campos del formulario.");
    }
    return consulta.obtener(configuracion.crear(juego, elementos));
  }

  private NegocioException invalido() {
    return new NegocioException(
        HttpStatus.BAD_REQUEST,
        "juego-pesos-invalido",
        "Configuración de pesos inválida",
        "Revisa el acta, las referencias únicas y los pesos positivos con un máximo de tres decimales y 999.999.");
  }
}
