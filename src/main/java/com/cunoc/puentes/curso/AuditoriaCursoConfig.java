package com.cunoc.puentes.curso;

import com.cunoc.puentes.curso.dto.AsignacionDTO;
import com.cunoc.puentes.curso.dto.CursoDTO;
import com.cunoc.puentes.curso.dto.InscripcionDTO;
import java.util.Map;
import java.util.UUID;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Aspecto Spring AOP limitado a P7; escritura y auditoría comparten transacción (DT-BD-12). */
@Configuration
class AuditoriaCursoConfig {
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  static Advisor auditoriaCursoAdvisor(
      ObjectProvider<AuditoriaCursoRepository> repositorios,
      ObjectProvider<PlatformTransactionManager> gestores) {
    MethodInterceptor interceptor =
        invocacion -> {
          AuditoriaCursoRepository repositorio = repositorios.getObject();
          TransactionTemplate transaccion = new TransactionTemplate(gestores.getObject());
          return transaccion.execute(
              estado -> {
                Object[] argumentos = invocacion.getArguments();
                UUID usuarioId = (UUID) argumentos[0];
                String accion = invocacion.getMethod().getAnnotation(AuditarCurso.class).value();
                String anterior =
                    "MODIFICAR_CURSO".equals(accion)
                        ? repositorio.cursoAnterior((UUID) argumentos[1], usuarioId).orElse(null)
                        : null;
                Object resultado;
                try {
                  resultado = invocacion.proceed();
                } catch (RuntimeException | Error ex) {
                  throw ex;
                } catch (Throwable ex) {
                  throw new IllegalStateException(
                      "No se pudo completar la operación de cursos.", ex);
                }
                registrar(repositorio, usuarioId, accion, anterior, resultado);
                return resultado;
              });
        };
    return new DefaultPointcutAdvisor(
        new AnnotationMatchingPointcut(null, AuditarCurso.class), interceptor);
  }

  private static void registrar(
      AuditoriaCursoRepository repositorio,
      UUID usuarioId,
      String accion,
      String anterior,
      Object resultado) {
    JsonMapper json = JsonMapper.builder().build();
    switch (resultado) {
      case CursoDTO curso ->
          repositorio.registrar(
              usuarioId,
              accion,
              "curso",
              curso.id(),
              anterior,
              json.writeValueAsString(
                  Map.of(
                      "id",
                      curso.id(),
                      "anio",
                      curso.anio(),
                      "semestre",
                      curso.semestre(),
                      "catedratico_id",
                      curso.catedraticoId(),
                      "vigente",
                      curso.vigente())));
      case InscripcionDTO inscripcion ->
          repositorio.registrar(
              usuarioId,
              accion,
              "inscripcion",
              inscripcion.id(),
              null,
              json.writeValueAsString(
                  Map.of(
                      "curso_id",
                      inscripcion.cursoId(),
                      "estudiante_id",
                      inscripcion.estudianteId())));
      case AsignacionDTO asignacion ->
          repositorio.registrar(
              usuarioId,
              accion,
              "asignacion",
              asignacion.id(),
              null,
              json.writeValueAsString(
                  Map.of(
                      "inscripcion_id",
                      asignacion.inscripcionId(),
                      "puente_id",
                      asignacion.puenteId())));
      default -> throw new IllegalStateException("Resultado de auditoría no soportado.");
    }
  }
}
