package com.cunoc.puentes.formulario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.formulario.dto.CrearFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.EditarFormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.FormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PublicarFormularioVersionDTO;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class FormularioVersionServiceTest {

  @Mock private FormularioVersionRepository repository;
  @Mock private FormularioValidacionServiceImpl validacionService;

  private final FormularioVersionMapper mapper = Mappers.getMapper(FormularioVersionMapper.class);
  private FormularioVersionService service;

  private final Map<String, Object> esquemaBase =
      Map.of("type", "object", "properties", Map.of("campo", Map.of("type", "string")));

  @BeforeEach
  void setUp() {
    service = new FormularioVersionService(repository, mapper, validacionService);
  }

  @Test
  @DisplayName("obtenerActivo debe retornar la versión activa cuando existe")
  void obtenerActivo_existe_retornaDTO() {
    FormularioVersion activa = new FormularioVersion("V1.0.0", esquemaBase);
    activa.publicar(null);
    when(repository.findByEstado(EstadoFormulario.ACTIVA)).thenReturn(Optional.of(activa));

    FormularioVersionDTO dto = service.obtenerActivo();

    assertThat(dto.codigo()).isEqualTo("V1.0.0");
    assertThat(dto.estado()).isEqualTo(EstadoFormulario.ACTIVA);
  }

  @Test
  @DisplayName("obtenerActivo debe lanzar 404 si no hay ninguna versión activa")
  void obtenerActivo_noExiste_lanza404() {
    when(repository.findByEstado(EstadoFormulario.ACTIVA)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.obtenerActivo())
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  @DisplayName("crearBorrador debe rechazar códigos duplicados con 409")
  void crearBorrador_codigoDuplicado_lanza409() {
    when(repository.existsByCodigo("V1.0.0")).thenReturn(true);
    CrearFormularioVersionDTO dto = new CrearFormularioVersionDTO("V1.0.0", esquemaBase);

    assertThatThrownBy(() -> service.crearBorrador(dto))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  @DisplayName("crearBorrador debe guardar un nuevo borrador cuando el código es único")
  void crearBorrador_codigoUnico_guardaBorrador() {
    when(repository.existsByCodigo("V2.0.0")).thenReturn(false);
    when(repository.save(any(FormularioVersion.class))).thenAnswer(inv -> inv.getArgument(0));

    CrearFormularioVersionDTO dto = new CrearFormularioVersionDTO("V2.0.0", esquemaBase);
    FormularioVersionDTO creado = service.crearBorrador(dto);

    assertThat(creado.codigo()).isEqualTo("V2.0.0");
    assertThat(creado.estado()).isEqualTo(EstadoFormulario.BORRADOR);
  }

  @Test
  @DisplayName("editarBorrador debe rechazar la modificación de versiones publicadas (RN-FRM-03)")
  void editarBorrador_versionActiva_lanza409() {
    UUID id = UUID.randomUUID();
    FormularioVersion version = new FormularioVersion("V1.0.0", esquemaBase);
    version.publicar(null);
    when(repository.findById(id)).thenReturn(Optional.of(version));

    EditarFormularioVersionDTO dto = new EditarFormularioVersionDTO(Map.of("type", "object"));

    assertThatThrownBy(() -> service.editarBorrador(id, dto))
        .isInstanceOf(NegocioException.class)
        .satisfies(
            ex ->
                assertThat(((NegocioException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  @DisplayName(
      "publicar debe retirar la versión activa previa y activar la nueva con su mapeo (RN-FRM-06)")
  void publicar_conActivaPrevia_retiraPreviaYActivaNueva() {
    UUID idNueva = UUID.randomUUID();
    FormularioVersion borrador = new FormularioVersion("V2.0.0", esquemaBase);

    FormularioVersion activaPrevia = new FormularioVersion("V1.0.0", esquemaBase);
    activaPrevia.publicar(null);

    when(repository.findById(idNueva)).thenReturn(Optional.of(borrador));
    when(repository.findByEstado(EstadoFormulario.ACTIVA)).thenReturn(Optional.of(activaPrevia));
    when(repository.save(any(FormularioVersion.class))).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> mapeo = Map.of("campo", "campo_renombrado");
    FormularioVersionDTO publicada =
        service.publicar(idNueva, new PublicarFormularioVersionDTO(mapeo));

    assertThat(publicada.estado()).isEqualTo(EstadoFormulario.ACTIVA);
    assertThat(publicada.mapeo()).isEqualTo(mapeo);
    assertThat(activaPrevia.getEstado()).isEqualTo(EstadoFormulario.RETIRADA);
    verify(repository).save(activaPrevia);
    verify(repository).save(borrador);
  }
}
