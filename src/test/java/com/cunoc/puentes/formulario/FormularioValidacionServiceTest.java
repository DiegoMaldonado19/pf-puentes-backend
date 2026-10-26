package com.cunoc.puentes.formulario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class FormularioValidacionServiceTest {

  @Mock private FormularioVersionRepository repository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private FormularioValidacionServiceImpl service;

  private Map<String, Object> esquemaPrueba;

  @BeforeEach
  void setUp() {
    service = new FormularioValidacionServiceImpl(repository, objectMapper);

    // Esquema de prueba representativo (Contratos 1 y 2)
    esquemaPrueba =
        Map.of(
            "$schema",
            "http://json-schema.org/draft-07/schema#",
            "type",
            "object",
            "required",
            List.of("codigo_puente", "subestructura"),
            "properties",
            Map.of(
                "codigo_puente",
                Map.of("type", "string", "x-permanente", true),
                "subestructura",
                Map.of(
                    "type",
                    "object",
                    "required",
                    List.of("dano_estribo"),
                    "properties",
                    Map.of(
                        "dano_estribo",
                        Map.of(
                            "type",
                            "integer",
                            "minimum",
                            0,
                            "maximum",
                            4,
                            "x-dano",
                            true,
                            "x-elemento-ref",
                            "subestructura.dano_estribo")))));
  }

  @Test
  @DisplayName("Constructor con repositorio por defecto inicializa correctamente")
  void constructorPorDefecto_inicializaCorrectamente() {
    FormularioValidacionServiceImpl impl = new FormularioValidacionServiceImpl(repository);
    assertThat(impl).isNotNull();
  }

  @Test
  @DisplayName("Datos válidos deben pasar la validación sin lanzar excepción")
  void validarConEsquema_datosValidos_noLanzaExcepcion() {
    Map<String, Object> datos =
        Map.of("codigo_puente", "GT-09-0901-0001", "subestructura", Map.of("dano_estribo", 2));

    assertThatCode(() -> service.validarConEsquema(esquemaPrueba, datos))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Falta de campo requerido debe fallar con 422 y listar el error")
  void validarConEsquema_faltaCampoRequerido_lanza422() {
    Map<String, Object> datos = Map.of("codigo_puente", "GT-09-0901-0001");

    assertThatThrownBy(() -> service.validarConEsquema(esquemaPrueba, datos))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex -> {
              NegocioException negEx = (NegocioException) ex;
              assertThat(negEx.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(negEx.getBody().getProperties()).containsKey("errores");
            });
  }

  @Test
  @DisplayName("Calificación de daño mayor a 4 debe fallar con 422")
  void validarConEsquema_danoFueraDeRango_lanza422() {
    Map<String, Object> datos =
        Map.of("codigo_puente", "GT-09-0901-0001", "subestructura", Map.of("dano_estribo", 5));

    assertThatThrownBy(() -> service.validarConEsquema(esquemaPrueba, datos))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex -> {
              NegocioException negEx = (NegocioException) ex;
              assertThat(negEx.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            });
  }

  @Test
  @DisplayName("Validación por versión inexistente debe fallar con 404")
  void validar_versionInexistente_lanza404() {
    UUID versionId = UUID.randomUUID();
    when(repository.findById(versionId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.validar(versionId, Map.of()))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex -> {
              NegocioException negEx = (NegocioException) ex;
              assertThat(negEx.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
  }

  @Test
  @DisplayName("Validación por versión existente en repositorio debe delegar al esquema")
  void validar_versionExistente_validaCorrectamente() {
    UUID versionId = UUID.randomUUID();
    FormularioVersion version = new FormularioVersion("V1.0.0", esquemaPrueba);
    when(repository.findById(versionId)).thenReturn(Optional.of(version));

    Map<String, Object> datosValidos =
        Map.of("codigo_puente", "GT-09-0901-0001", "subestructura", Map.of("dano_estribo", 0));

    assertThatCode(() -> service.validar(versionId, datosValidos)).doesNotThrowAnyException();
  }
}
