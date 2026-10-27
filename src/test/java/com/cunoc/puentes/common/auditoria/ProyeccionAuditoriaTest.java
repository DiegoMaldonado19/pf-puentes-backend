package com.cunoc.puentes.common.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.foro.dto.ResultadoModeracion;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ProyeccionAuditoriaTest {

  public Map<String, Object> operacion() {
    return Map.of();
  }

  @Auditable(
      accion = Acciones.MODERAR,
      entidad = "comentario",
      entidadId = "#id",
      motivo = "#motivo",
      condicion = "#result.modificado",
      valorAnterior = "#result.valoresAnteriores('oculto')",
      valorNuevo = "#result.valoresNuevos('oculto')")
  public ResultadoModeracion moderarConProyeccion(UUID id, String motivo) {
    return new ResultadoModeracion(
        true, id, Instant.parse("2026-10-10T12:00:00Z"), motivo, List.of(id));
  }

  @Auditable(accion = Acciones.CREAR, entidad = "hilo", entidadId = "#result")
  public UUID crearConIdEnResultado() {
    return UUID.randomUUID();
  }

  @Auditable(accion = Acciones.MODIFICAR, entidad = "hilo", entidadId = "#id")
  public UUID actualizarSinProyeccion(UUID id) {
    return id;
  }

  @Test
  void proyeccionDelResultadoPasaPorSaneadorIncluidosMapasAnidados() throws Exception {
    var evaluador = new EvaluadorDeExpresiones();
    var resultado =
        Map.of(
            "estado",
            "OCULTO",
            "token",
            "secreto",
            "detalle",
            Map.of("password", "secreto", "correo", "ana@example.com"));
    var limpio =
        evaluador.valores("#result", getClass().getMethod("operacion"), new Object[0], resultado);
    assertThat(limpio).containsEntry("estado", "OCULTO").doesNotContainKey("token");
    assertThat(limpio.toString())
        .doesNotContain("secreto", "ana@example.com")
        .contains("a***@example.com");
  }

  @Test
  void unaProyeccionQueNoDevuelveMapaFallaEnLugarDeRegistrarDatosArbitrarios() throws Exception {
    var metodo = getClass().getMethod("operacion");
    assertThatThrownBy(
            () -> new EvaluadorDeExpresiones().valores("#result", metodo, new Object[0], "texto"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void evaluadorDeExpresionesCubreMotivoCondicionYConversionUuid() throws Exception {
    var evaluador = new EvaluadorDeExpresiones();
    Method metodo = getClass().getMethod("moderarConProyeccion", UUID.class, String.class);
    UUID id = UUID.fromString("0192f5a0-0000-7000-8000-000000000030");

    assertThat(evaluador.motivo("", metodo, new Object[] {id, "Motivo"})).isNull();
    assertThat(evaluador.motivo("#motivo", metodo, new Object[] {id, null})).isNull();
    assertThat(evaluador.motivo("#motivo", metodo, new Object[] {id, "   "})).isNull();
    assertThat(evaluador.motivo("#motivo", metodo, new Object[] {id, "  Justificado "}))
        .isEqualTo("Justificado");

    assertThat(evaluador.cumple("", metodo, new Object[] {id, "x"}, null)).isTrue();
    assertThat(
            evaluador.cumple(
                "#result.modificado",
                metodo,
                new Object[] {id, "x"},
                new ResultadoModeracion(false, id, null, null, List.of())))
        .isFalse();

    assertThat(evaluador.idPrevio("#result.entidadId", metodo, new Object[] {id, "x"})).isNull();
    assertThat(evaluador.id("'" + id + "'", metodo, new Object[] {id, "x"}, null)).isEqualTo(id);
    assertThat(evaluador.id("null", metodo, new Object[] {id, "x"}, null)).isNull();
    assertThatThrownBy(() -> evaluador.id("123", metodo, new Object[] {id, "x"}, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aspectoAuditoriaUsaProyeccionesValorAnteriorYValorNuevoYCubreRamas() throws Throwable {
    PlatformTransactionManager gestor = mock(PlatformTransactionManager.class);
    when(gestor.getTransaction(any())).thenAnswer(_ -> new SimpleTransactionStatus());

    EstadoDeEntidad estados = mock(EstadoDeEntidad.class);
    AuditoriaService auditoria = mock(AuditoriaService.class);
    EvaluadorDeExpresiones expresiones = new EvaluadorDeExpresiones();
    ContextoAuditoria contexto = new ContextoAuditoria();

    AspectoAuditoria aspecto =
        new AspectoAuditoria(gestor, estados, expresiones, auditoria, contexto);

    UUID id = UUID.fromString("0192f5a0-0000-7000-8000-000000000030");
    Method metodo = getClass().getMethod("moderarConProyeccion", UUID.class, String.class);
    Auditable auditable = metodo.getAnnotation(Auditable.class);
    ResultadoModeracion retorno = moderarConProyeccion(id, "Contenido ofensivo");

    ProceedingJoinPoint punto = mock(ProceedingJoinPoint.class);
    MethodSignature firma = mock(MethodSignature.class);
    when(punto.getSignature()).thenReturn(firma);
    when(firma.getMethod()).thenReturn(metodo);
    when(punto.getArgs()).thenReturn(new Object[] {id, "Contenido ofensivo"});
    when(punto.proceed()).thenReturn(retorno);

    Object devuelto = aspecto.auditar(punto, auditable);

    assertThat(devuelto).isEqualTo(retorno);
    verify(auditoria)
        .registrar(
            eq(Acciones.MODERAR),
            eq("comentario"),
            eq(id),
            eq(Map.of("oculto", false)),
            any(),
            eq("Contenido ofensivo"));

    ResultadoModeracion sinCambios = new ResultadoModeracion(false, id, null, null, List.of());
    when(punto.proceed()).thenReturn(sinCambios);
    assertThat(aspecto.auditar(punto, auditable)).isEqualTo(sinCambios);

    Method metodoCrear = getClass().getMethod("crearConIdEnResultado");
    Auditable auditableCrear = metodoCrear.getAnnotation(Auditable.class);
    when(firma.getMethod()).thenReturn(metodoCrear);
    when(punto.getArgs()).thenReturn(new Object[0]);
    when(punto.proceed()).thenReturn(id);
    when(estados.leer("hilo", id)).thenReturn(Optional.of(Map.of("id", id.toString())));
    assertThat(aspecto.auditar(punto, auditableCrear)).isEqualTo(id);

    when(punto.proceed()).thenReturn(null);
    assertThatThrownBy(() -> aspecto.auditar(punto, auditableCrear))
        .isInstanceOf(IllegalStateException.class);

    Method metodoActualizar = getClass().getMethod("actualizarSinProyeccion", UUID.class);
    Auditable auditableActualizar = metodoActualizar.getAnnotation(Auditable.class);
    when(firma.getMethod()).thenReturn(metodoActualizar);
    when(punto.getArgs()).thenReturn(new Object[] {id});
    when(punto.proceed()).thenReturn(id);
    assertThat(aspecto.auditar(punto, auditableActualizar)).isEqualTo(id);

    doThrow(new IOException("fallo comprobado")).when(punto).proceed();
    assertThatThrownBy(() -> aspecto.auditar(punto, auditableActualizar))
        .isInstanceOf(IOException.class);

    doThrow(new IllegalArgumentException("fallo runtime")).when(punto).proceed();
    assertThatThrownBy(() -> aspecto.auditar(punto, auditableActualizar))
        .isInstanceOf(IllegalArgumentException.class);
    verify(gestor, never()).rollback((TransactionStatus) null);
  }
}
