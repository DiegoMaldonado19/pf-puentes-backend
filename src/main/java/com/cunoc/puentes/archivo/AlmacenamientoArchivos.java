package com.cunoc.puentes.archivo;

/** DT-ALM-03: el dominio guarda y borra archivos sin saber qué tecnología hay detrás. */
public interface AlmacenamientoArchivos {

  // ponytail: el archivo completo en memoria (hasta 20 MB); pasar a InputStream si las subidas
  // concurrentes de PDF aprietan el heap
  void guardar(String clave, byte[] contenido, String tipoMime);

  /** DT-SEC-09: el navegador descarga el objeto sin token, solo mientras la URL esté vigente. */
  String obtenerUrlFirmada(String clave);

  void eliminar(String clave);
}
