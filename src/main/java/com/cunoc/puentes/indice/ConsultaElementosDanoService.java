package com.cunoc.puentes.indice;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Referencias del esquema real para preparar pesos, sin interpretar respuestas. */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ConsultaElementosDanoService {
  private final MetadatosDanoFormulario metadatos;

  ConsultaElementosDanoService(MetadatosDanoFormulario metadatos) {
    this.metadatos = metadatos;
  }

  public Page<String> listar(UUID version, int pagina, int tamano) {
    if (pagina < 0 || tamano < 1 || tamano > 100 || (long) pagina * tamano > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("La página solicitada no es válida.");
    }
    var referencias = metadatos.obtener(version).referencias().stream().sorted().toList();
    long inicio = (long) pagina * tamano;
    var contenido = referencias.stream().skip(inicio).limit(tamano).toList();
    return new PageImpl<>(contenido, PageRequest.of(pagina, tamano), referencias.size());
  }
}
