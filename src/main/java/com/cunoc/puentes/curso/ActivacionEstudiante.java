package com.cunoc.puentes.curso;

import java.util.UUID;

/** Puerto de P2: debe participar en la transacción de inscripción, validar y auditar la cuenta. */
@FunctionalInterface
public interface ActivacionEstudiante {
  void activar(UUID estudianteId, UUID catedraticoId);
}
