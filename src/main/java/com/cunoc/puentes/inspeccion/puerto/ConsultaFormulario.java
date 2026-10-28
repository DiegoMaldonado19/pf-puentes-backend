package com.cunoc.puentes.inspeccion.puerto;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Puerto hacia P4 (contratos 1 y 2): P5 no importa el servicio de formularios. */
public interface ConsultaFormulario {

  Optional<VersionFormularioVista> versionActiva();

  Set<String> rutasPermanentes(UUID versionId);

  Set<String> rutasDeDano(UUID versionId);

  List<GrupoElemento> grupos(UUID versionId);

  void validarParcial(Map<String, Object> datos, UUID versionId);

  void validarCompleto(Map<String, Object> datos, UUID versionId);
}
