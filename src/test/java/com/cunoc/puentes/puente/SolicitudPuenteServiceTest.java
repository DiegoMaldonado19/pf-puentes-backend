package com.cunoc.puentes.puente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.puente.dto.AprobarSolicitudDTO;
import com.cunoc.puentes.puente.dto.CrearPuenteDTO;
import com.cunoc.puentes.puente.dto.CrearSolicitudPuenteDTO;
import com.cunoc.puentes.puente.dto.PuenteDTO;
import com.cunoc.puentes.puente.dto.RechazarSolicitudDTO;
import com.cunoc.puentes.puente.dto.SolicitudPuenteDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SolicitudPuenteServiceTest {

  private static final UUID ID = UUID.fromString("0192f5a0-0000-7000-8000-000000000401");
  private static final UUID CATEDRATICO = UUID.fromString("0192f5a0-0000-7000-8000-000000000402");
  private static final UUID OTRO = UUID.fromString("0192f5a0-0000-7000-8000-000000000403");
  private static final UUID ADMIN = UUID.fromString("0192f5a0-0000-7000-8000-000000000404");
  private static final UUID PUENTE = UUID.fromString("0192f5a0-0000-7000-8000-000000000405");
  private static final CrearSolicitudPuenteDTO DATOS =
      new CrearSolicitudPuenteDTO(" Las Rosas ", "09", "0901", " CA-1 ", 14.8347, -91.518, null);

  @Mock SolicitudPuenteRepository solicitudes;
  @Mock PuenteRepository puentes;
  @Mock PuenteService puenteService;

  SolicitudPuenteService servicio() {
    return new SolicitudPuenteService(
        solicitudes,
        puentes,
        puenteService,
        new SolicitudPuenteMapperImpl(new CatalogoIneMapperImpl()));
  }

  static SolicitudPuente solicitud(EstadoSolicitud estado) {
    SolicitudPuente solicitud = new SolicitudPuente();
    ReflectionTestUtils.setField(solicitud, "id", ID);
    solicitud.setCatedraticoId(CATEDRATICO);
    solicitud.setNombre("Las Rosas");
    solicitud.setMunicipio(Catalogo.MUNICIPIO_XELA);
    solicitud.setRuta("CA-1");
    solicitud.setUbicacion(PuenteService.punto(14.8347, -91.518));
    solicitud.setEstado(estado);
    return solicitud;
  }

  static void esError(Runnable accion, HttpStatus estado, String tipo) {
    PuenteServiceTest.esError(accion, estado, tipo);
  }

  @Test
  void elCatedraticoSolicitaYQuedaPendiente() {
    when(puenteService.municipioCongruente("09", "0901")).thenReturn(Catalogo.MUNICIPIO_XELA);
    when(solicitudes.save(any())).thenAnswer(i -> i.getArgument(0));

    SolicitudPuenteDTO creada = servicio().solicitar(CATEDRATICO, DATOS);

    verify(puenteService).advertirDuplicados(14.8347, -91.518, null, false);
    assertThat(creada.estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
    assertThat(creada.catedraticoId()).isEqualTo(CATEDRATICO);
    assertThat(creada.nombre()).isEqualTo("Las Rosas");
    assertThat(creada.ruta()).isEqualTo("CA-1");
    assertThat(creada.utm().zona()).isEqualTo(15);
    assertThat(creada.puenteId()).isNull();
  }

  @Test
  void laSolicitudTambienAdvierteDuplicados() {
    when(puenteService.municipioCongruente("09", "0901")).thenReturn(Catalogo.MUNICIPIO_XELA);
    doThrow(new NegocioException(HttpStatus.CONFLICT, "posible-duplicado", "Duplicado", "x"))
        .when(puenteService)
        .advertirDuplicados(anyDouble(), anyDouble(), any(), anyBoolean());

    esError(
        () -> servicio().solicitar(CATEDRATICO, DATOS), HttpStatus.CONFLICT, "posible-duplicado");
    verify(solicitudes, never()).save(any());
  }

  @Test
  void listaPaginadoYValidaLaPaginacion() {
    ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
    when(solicitudes.findAll(any(Specification.class), pagina.capture()))
        .thenReturn(
            new PageImpl<>(
                List.of(solicitud(EstadoSolicitud.PENDIENTE)), PageRequest.of(0, 20), 1));

    var resultado = servicio().listar(CATEDRATICO, false, EstadoSolicitud.PENDIENTE, 0, 20);

    assertThat(resultado.contenido())
        .singleElement()
        .extracting(SolicitudPuenteDTO::id)
        .isEqualTo(ID);
    assertThat(pagina.getValue().getSort().getOrderFor("creadoEn").isDescending()).isTrue();
    assertThat(servicio().listar(ADMIN, true, null, 0, 20).totalElementos()).isEqualTo(1);
    esError(
        () -> servicio().listar(ADMIN, true, null, 0, 101),
        HttpStatus.BAD_REQUEST,
        "paginacion-invalida");
    esError(
        () -> servicio().listar(ADMIN, true, null, -1, 20),
        HttpStatus.BAD_REQUEST,
        "paginacion-invalida");
  }

  @Test
  void otroCatedraticoNoPuedeSaberQueExiste() {
    when(solicitudes.findConCatalogoById(ID))
        .thenReturn(Optional.of(solicitud(EstadoSolicitud.PENDIENTE)));

    assertThat(servicio().detalle(ID, CATEDRATICO, false).id()).isEqualTo(ID);
    assertThat(servicio().detalle(ID, ADMIN, true).id()).isEqualTo(ID);
    esError(
        () -> servicio().detalle(ID, OTRO, false), HttpStatus.NOT_FOUND, "solicitud-no-encontrada");
  }

  @Test
  void aprobarCreaElPuenteConLosDatosDeLaSolicitud() {
    SolicitudPuente pendiente = solicitud(EstadoSolicitud.PENDIENTE);
    when(solicitudes.bloquear(ID)).thenReturn(Optional.of(pendiente));
    PuenteDTO creado = org.mockito.Mockito.mock(PuenteDTO.class);
    when(creado.id()).thenReturn(PUENTE);
    when(puenteService.crear(any())).thenReturn(creado);
    Puente referencia = PuenteServiceTest.puente(true);
    ReflectionTestUtils.setField(referencia, "id", PUENTE);
    when(puentes.getReferenceById(PUENTE)).thenReturn(referencia);

    SolicitudPuenteDTO aprobada = servicio().aprobar(ID, ADMIN, new AprobarSolicitudDTO(true));

    ArgumentCaptor<CrearPuenteDTO> alta = ArgumentCaptor.forClass(CrearPuenteDTO.class);
    verify(puenteService).crear(alta.capture());
    assertThat(alta.getValue())
        .isEqualTo(
            new CrearPuenteDTO("Las Rosas", "09", "0901", "CA-1", 14.8347, -91.518, null, true));
    assertThat(aprobada.estado()).isEqualTo(EstadoSolicitud.APROBADA);
    assertThat(aprobada.administradorId()).isEqualTo(ADMIN);
    assertThat(aprobada.puenteId()).isEqualTo(PUENTE);
  }

  @Test
  void rechazarGuardaElMotivoYQuienResolvio() {
    when(solicitudes.bloquear(ID)).thenReturn(Optional.of(solicitud(EstadoSolicitud.PENDIENTE)));

    SolicitudPuenteDTO rechazada =
        servicio().rechazar(ID, ADMIN, new RechazarSolicitudDTO("  Ya existe  "));

    assertThat(rechazada.estado()).isEqualTo(EstadoSolicitud.RECHAZADA);
    assertThat(rechazada.motivoRechazo()).isEqualTo("Ya existe");
    assertThat(rechazada.administradorId()).isEqualTo(ADMIN);
  }

  @ParameterizedTest
  @EnumSource(
      value = EstadoSolicitud.class,
      names = {"APROBADA", "RECHAZADA"})
  void unaSolicitudResueltaNoSeVuelveAResolver(EstadoSolicitud estado) {
    when(solicitudes.bloquear(ID)).thenReturn(Optional.of(solicitud(estado)));

    esError(
        () -> servicio().aprobar(ID, ADMIN, new AprobarSolicitudDTO(null)),
        HttpStatus.CONFLICT,
        "solicitud-ya-resuelta");
    esError(
        () -> servicio().rechazar(ID, ADMIN, new RechazarSolicitudDTO("x")),
        HttpStatus.CONFLICT,
        "solicitud-ya-resuelta");
    verify(puenteService, never()).crear(any());
  }

  @Test
  void unaSolicitudInexistenteEs404() {
    when(solicitudes.bloquear(ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio().rechazar(ID, ADMIN, new RechazarSolicitudDTO("x")))
        .isInstanceOf(NegocioException.class)
        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
  }
}
