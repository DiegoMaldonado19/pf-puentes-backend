package com.cunoc.puentes.common.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Un @Auditable mal declarado impide el arranque, con el motivo en el mensaje. */
class ValidadorDeAuditablesTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner()
          .withBean(ValidadorDeAuditables.class)
          .withBean(EvaluadorDeExpresiones.class);

  static class ServicioValido {
    @Auditable(accion = Acciones.CAMBIAR_ESTADO, entidad = "inspeccion", entidadId = "#id")
    void cambiar(java.util.UUID id) {}

    @Auditable(accion = "DAR_DE_BAJA", entidad = "usuario", entidadId = "#id", motivo = "#motivo")
    void baja(java.util.UUID id, String motivo) {}

    @Auditable(accion = Acciones.CREAR, entidad = "puente", entidadId = "#result.id")
    Object crear() {
      return null;
    }
  }

  static class AccionEnMinusculas {
    @Auditable(accion = "crear", entidad = "puente")
    Object crear() {
      return null;
    }
  }

  static class EntidadConMayuscula {
    @Auditable(accion = Acciones.CREAR, entidad = "Puente")
    Object crear() {
      return null;
    }
  }

  static class AccionFueraDelCatalogo {
    @Auditable(accion = "PUBLICAR", entidad = "puente")
    Object publicar() {
      return null;
    }
  }

  static class ExpresionInvalida {
    @Auditable(accion = Acciones.MODIFICAR, entidad = "usuario", entidadId = "#id.(")
    void modificar(java.util.UUID id) {}
  }

  @Test
  void seRegistraConAccionYEntidadValidas() {
    contexto.withBean(ServicioValido.class).run(c -> assertThat(c).hasNotFailed());
  }

  @Test
  void unaAccionEnMinusculasImpideElArranque() {
    contexto
        .withBean(AccionEnMinusculas.class)
        .run(
            c ->
                assertThat(c)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining("AccionEnMinusculas.crear")
                    .hasMessageContaining("'crear'"));
  }

  @Test
  void unaEntidadConMayusculasImpideElArranque() {
    contexto
        .withBean(EntidadConMayuscula.class)
        .run(c -> assertThat(c).hasFailed().getFailure().hasMessageContaining("'Puente'"));
  }

  @Test
  void unaAccionFueraDelCatalogoImpideElArranque() {
    contexto
        .withBean(AccionFueraDelCatalogo.class)
        .run(
            c ->
                assertThat(c)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining("catálogo")
                    .hasMessageContaining("'PUBLICAR'"));
  }

  @Test
  void unaExpresionInvalidaImpideElArranque() {
    contexto
        .withBean(ExpresionInvalida.class)
        .run(c -> assertThat(c).hasFailed().getFailure().hasMessageContaining("#id.("));
  }

  static class CondicionInvalida {
    @Auditable(
        accion = Acciones.MODIFICAR,
        entidad = "usuario",
        entidadId = "#id",
        condicion = "#a.(")
    void modificar(java.util.UUID id) {}
  }

  @Test
  void unaCondicionInvalidaImpideElArranque() {
    contexto
        .withBean(CondicionInvalida.class)
        .run(c -> assertThat(c).hasFailed().getFailure().hasMessageContaining("#a.("));
  }

  @Test
  void unaEntidadQueNoExisteComoEntidadJpaImpideElArranque() {
    EstadoDeEntidad estados = mock(EstadoDeEntidad.class);
    when(estados.existe("usuario")).thenReturn(false);

    contexto
        .withBean(EstadoDeEntidad.class, () -> estados)
        .withBean(ServicioValido.class)
        .run(
            c ->
                assertThat(c)
                    .hasFailed()
                    .getFailure()
                    .hasMessageContaining("no existe una entidad JPA llamada 'usuario'"));
  }
}
