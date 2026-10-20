package com.cunoc.puentes.curso;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.curso.dto.InscribirEstudianteDTO;
import com.cunoc.puentes.usuario.EstadoCuentaService;
import com.cunoc.puentes.usuario.Rol;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class CursoServiceTest {
  @Mock CursoRepository cursos;
  @Mock InscripcionRepository inscripciones;
  @Mock AsignacionRepository asignaciones;
  @Mock ReferenciasCursoRepository referencias;
  @Mock CursoMapper mapper;
  @Mock ObjectProvider<ActivacionEstudiante> activacion;
  @Mock EstadoCuentaService cuentas;
  CursoService servicio;
  final UUID docente = UUID.randomUUID();
  final UUID cursoId = UUID.randomUUID();
  final UUID estudiante = UUID.randomUUID();

  @BeforeEach
  void preparar() {
    servicio =
        new CursoService(
            cursos, inscripciones, asignaciones, referencias, mapper, activacion, cuentas);
    when(cuentas.rolSiEstaActiva(docente)).thenReturn(Optional.of(Rol.CATEDRATICO));
    when(cursos.bloquearPropio(cursoId, docente)).thenReturn(Optional.of(new Curso()));
  }

  @Test
  void sinServicioDeP2NoActivaNiGuardaInscripcionPendiente() {
    when(referencias.buscarCuenta(estudiante, true))
        .thenReturn(
            Optional.of(
                new ReferenciasCursoRepository.Cuenta("ESTUDIANTE", "PENDIENTE", Instant.now())));
    assertThatThrownBy(
            () -> servicio.inscribir(docente, cursoId, new InscribirEstudianteDTO(estudiante)))
        .isInstanceOfSatisfying(
            NegocioException.class,
            ex ->
                org.assertj.core.api.Assertions.assertThat(ex.getStatusCode().value())
                    .isEqualTo(503));
    verify(inscripciones, never()).saveAndFlush(any());
  }

  @Test
  void estudianteInexistenteResponde404SinGuardar() {
    when(referencias.buscarCuenta(estudiante, true)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> servicio.inscribir(docente, cursoId, new InscribirEstudianteDTO(estudiante)))
        .isInstanceOfSatisfying(
            NegocioException.class,
            ex ->
                org.assertj.core.api.Assertions.assertThat(ex.getStatusCode().value())
                    .isEqualTo(404));
    verify(inscripciones, never()).saveAndFlush(any());
  }
}
