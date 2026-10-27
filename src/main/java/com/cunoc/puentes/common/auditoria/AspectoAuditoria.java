package com.cunoc.puentes.common.auditoria;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RN-USR-09, DT-BD-12: registra los métodos {@link Auditable} en la capa de servicio.
 *
 * <p>Corre dentro de una transacción {@code REQUIRED}: se une a la del servicio o abre la suya, así
 * que da igual el orden frente a {@code @Transactional}. La operación y su fila se confirman juntas
 * o se revierten juntas.
 */
@Aspect
@Component
class AspectoAuditoria {

  private final TransactionTemplate transacciones;
  private final EstadoDeEntidad estados;
  private final EvaluadorDeExpresiones expresiones;
  private final AuditoriaService auditoria;
  private final ContextoAuditoria contexto;

  AspectoAuditoria(
      PlatformTransactionManager gestor,
      EstadoDeEntidad estados,
      EvaluadorDeExpresiones expresiones,
      AuditoriaService auditoria,
      ContextoAuditoria contexto) {
    this.transacciones = new TransactionTemplate(gestor);
    this.estados = estados;
    this.expresiones = expresiones;
    this.auditoria = auditoria;
    this.contexto = contexto;
  }

  @Around("@annotation(auditable)")
  Object auditar(ProceedingJoinPoint punto, Auditable auditable) throws Throwable {
    try (var _ = contexto.abrirCorrelacion()) {
      return transacciones.execute(estado -> ejecutar(punto, auditable));
    } catch (FalloEnvuelto e) {
      throw e.getCause();
    }
  }

  private Object ejecutar(ProceedingJoinPoint punto, Auditable auditable) {
    Method metodo = ((MethodSignature) punto.getSignature()).getMethod();
    Object[] argumentos = punto.getArgs();
    UUID idPrevio = expresiones.idPrevio(auditable.entidadId(), metodo, argumentos);
    Map<String, Object> anterior =
        idPrevio == null || !auditable.valorAnterior().isBlank()
            ? null
            : estados.leer(auditable.entidad(), idPrevio).orElse(null);
    String motivo = expresiones.motivo(auditable.motivo(), metodo, argumentos);

    Object resultado = proceder(punto);
    if (!expresiones.cumple(auditable.condicion(), metodo, argumentos, resultado)) {
      return resultado;
    }

    UUID id =
        idPrevio != null
            ? idPrevio
            : expresiones.id(auditable.entidadId(), metodo, argumentos, resultado);
    if (id == null) {
      throw new IllegalStateException(
          "No se pudo determinar el id auditable de %s".formatted(metodo.toGenericString()));
    }
    estados.sincronizar();
    if (!auditable.valorAnterior().isBlank()) {
      anterior = expresiones.valores(auditable.valorAnterior(), metodo, argumentos, resultado);
    }
    Map<String, Object> posterior =
        auditable.valorNuevo().isBlank()
            ? estados.leer(auditable.entidad(), id).orElse(null)
            : expresiones.valores(auditable.valorNuevo(), metodo, argumentos, resultado);
    auditoria.registrar(auditable.accion(), auditable.entidad(), id, anterior, posterior, motivo);
    return resultado;
  }

  private static Object proceder(ProceedingJoinPoint punto) {
    try {
      return punto.proceed();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new FalloEnvuelto(e); // excepción comprobada: el callback de la plantilla no la admite
    }
  }

  private static final class FalloEnvuelto extends RuntimeException {
    FalloEnvuelto(Throwable causa) {
      super(causa);
    }
  }
}
