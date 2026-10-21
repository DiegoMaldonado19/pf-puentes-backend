package com.cunoc.puentes.puente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.indice.EstadoCondicion;
import com.cunoc.puentes.puente.dto.CrearPuenteDTO;
import com.cunoc.puentes.puente.dto.DarDeBajaPuenteDTO;
import com.cunoc.puentes.puente.dto.EditarPuenteDTO;
import com.cunoc.puentes.puente.dto.PuenteCercanoDTO;
import com.cunoc.puentes.puente.dto.PuenteDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class PuenteServiceTest {

  private static final UUID ID = UUID.fromString("0192f5a0-0000-7000-8000-000000000301");
  private static final PuenteCercanoDTO CERCANO =
      new PuenteCercanoDTO(ID, "GT-09-0901-0001", "Las Rosas", 42.5);

  @Mock PuenteRepository puentes;
  @Mock MunicipioRepository municipios;
  @Mock GeneradorCodigoPuente generador;
  @Mock PuentesCercanos cercanos;

  PuenteService servicio() {
    return new PuenteService(
        puentes,
        municipios,
        generador,
        cercanos,
        new PuenteMapperImpl(new CatalogoIneMapperImpl()));
  }

  static CrearPuenteDTO alta(String depto, boolean confirmar) {
    return new CrearPuenteDTO(
        "  Puente Las Rosas ", depto, "0901", " CA-1 ", 14.8347, -91.518, 1985, confirmar);
  }

  static Puente puente(boolean activo) {
    Puente puente = new Puente();
    ReflectionTestUtils.setField(puente, "id", ID);
    puente.setCodigo("GT-09-0901-0001");
    puente.setNombre("Las Rosas");
    puente.setRuta("CA-1");
    puente.setMunicipio(Catalogo.MUNICIPIO_XELA);
    puente.setUbicacion(PuenteService.punto(14.8347, -91.518));
    puente.setActivo(activo);
    if (!activo) {
      puente.setMotivoBaja("Demolido");
    }
    return puente;
  }

  static void esError(Runnable accion, HttpStatus estado, String tipo) {
    assertThatThrownBy(accion::run)
        .isInstanceOfSatisfying(
            NegocioException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(estado);
              assertThat(e.getBody().getType().toString()).endsWith("/" + tipo);
            });
  }

  @Nested
  class Crear {

    @Test
    void asignaElCodigoDelSistemaYGuardaEnWgs84() {
      when(municipios.findById("0901")).thenReturn(Optional.of(Catalogo.MUNICIPIO_XELA));
      when(cercanos.buscar(14.8347, -91.518, null)).thenReturn(List.of());
      when(generador.siguiente(Catalogo.MUNICIPIO_XELA)).thenReturn("GT-09-0901-0007");
      when(puentes.save(any())).thenAnswer(i -> i.getArgument(0));

      PuenteDTO creado = servicio().crear(alta("09", false));

      ArgumentCaptor<Puente> guardado = ArgumentCaptor.forClass(Puente.class);
      verify(puentes).save(guardado.capture());
      assertThat(guardado.getValue().getUbicacion().getSRID()).isEqualTo(4326);
      assertThat(guardado.getValue().getUbicacion().getX()).isEqualTo(-91.518);
      assertThat(creado.codigo()).isEqualTo("GT-09-0901-0007");
      assertThat(creado.nombre()).isEqualTo("Puente Las Rosas");
      assertThat(creado.ruta()).isEqualTo("CA-1");
      assertThat(creado.anioConstruccion()).isEqualTo(1985);
      assertThat(creado.departamento().nombre()).isEqualTo("Quetzaltenango");
      assertThat(creado.utm().zona()).isEqualTo(15);
      assertThat(creado.estadoActual()).isNull(); // RN-INV-10: Sin evaluar
      assertThat(creado.activo()).isTrue();
    }

    @Test
    void unMunicipioInexistenteEs422() {
      when(municipios.findById("0901")).thenReturn(Optional.empty());

      esError(
          () -> servicio().crear(alta("09", false)),
          HttpStatus.UNPROCESSABLE_CONTENT,
          "municipio-no-encontrado");
      verify(generador, never()).siguiente(any());
    }

    @Test
    void unMunicipioDeOtroDepartamentoEs422() {
      when(municipios.findById("0901")).thenReturn(Optional.of(Catalogo.MUNICIPIO_XELA));

      esError(
          () -> servicio().crear(alta("01", false)),
          HttpStatus.UNPROCESSABLE_CONTENT,
          "municipio-incongruente");
      verify(puentes, never()).save(any());
    }

    @Test
    void advierteDuplicadosConLaListaYNoGuarda() {
      when(municipios.findById("0901")).thenReturn(Optional.of(Catalogo.MUNICIPIO_XELA));
      when(cercanos.buscar(14.8347, -91.518, null)).thenReturn(List.of(CERCANO));

      assertThatThrownBy(() -> servicio().crear(alta("09", false)))
          .isInstanceOfSatisfying(
              NegocioException.class,
              e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(e.getBody().getProperties()).containsEntry("cercanos", List.of(CERCANO));
              });
      verify(generador, never()).siguiente(any());
    }

    @Test
    void conLaAdvertenciaConfirmadaNoBloquea() {
      when(municipios.findById("0901")).thenReturn(Optional.of(Catalogo.MUNICIPIO_XELA));
      when(generador.siguiente(Catalogo.MUNICIPIO_XELA)).thenReturn("GT-09-0901-0002");
      when(puentes.save(any())).thenAnswer(i -> i.getArgument(0));

      assertThat(servicio().crear(alta("09", true)).codigo()).isEqualTo("GT-09-0901-0002");
      verify(cercanos, never()).buscar(anyDouble(), anyDouble(), any());
    }
  }

  @Nested
  class Editar {

    final EditarPuenteDTO cambios =
        new EditarPuenteDTO(" Nuevo nombre ", "RN-1", 14.84, -91.52, 1990, false);

    @Test
    void cambiaSoloLosDatosDescriptivos() {
      Puente puente = puente(true);
      puente.setEstadoActual(EstadoCondicion.MALO);
      when(puentes.findConCatalogoById(ID)).thenReturn(Optional.of(puente));
      when(cercanos.buscar(14.84, -91.52, ID)).thenReturn(List.of());

      PuenteDTO editado = servicio().editar(ID, cambios);

      assertThat(editado.nombre()).isEqualTo("Nuevo nombre");
      assertThat(editado.ruta()).isEqualTo("RN-1");
      assertThat(editado.latitud()).isEqualTo(14.84);
      assertThat(editado.anioConstruccion()).isEqualTo(1990);
      assertThat(editado.codigo()).isEqualTo("GT-09-0901-0001");
      assertThat(editado.estadoActual()).isEqualTo(EstadoCondicion.MALO);
    }

    @Test
    void unPuenteDadoDeBajaNoSeEdita() {
      when(puentes.findConCatalogoById(ID)).thenReturn(Optional.of(puente(false)));

      esError(() -> servicio().editar(ID, cambios), HttpStatus.CONFLICT, "puente-dado-de-baja");
    }

    @Test
    void unPuenteInexistenteEs404() {
      when(puentes.findConCatalogoById(ID)).thenReturn(Optional.empty());

      esError(() -> servicio().editar(ID, cambios), HttpStatus.NOT_FOUND, "puente-no-encontrado");
    }

    @Test
    void advierteDuplicadosExcluyendoAlMismoPuente() {
      when(puentes.findConCatalogoById(ID)).thenReturn(Optional.of(puente(true)));
      when(cercanos.buscar(14.84, -91.52, ID)).thenReturn(List.of(CERCANO));

      esError(() -> servicio().editar(ID, cambios), HttpStatus.CONFLICT, "posible-duplicado");
    }
  }

  @Nested
  class DarDeBaja {

    @Test
    void marcaInactivoConElMotivoSinBorrar() {
      when(puentes.findConCatalogoById(ID)).thenReturn(Optional.of(puente(true)));

      PuenteDTO baja = servicio().darDeBaja(ID, new DarDeBajaPuenteDTO("  Demolido  "));

      assertThat(baja.activo()).isFalse();
      assertThat(baja.motivoBaja()).isEqualTo("Demolido");
      verify(puentes, never()).delete(any(Puente.class));
      verify(puentes, never()).deleteById(any());
    }

    @Test
    void unaSegundaBajaEs409() {
      when(puentes.findConCatalogoById(ID)).thenReturn(Optional.of(puente(false)));

      esError(
          () -> servicio().darDeBaja(ID, new DarDeBajaPuenteDTO("Otra vez")),
          HttpStatus.CONFLICT,
          "puente-dado-de-baja");
    }
  }

  @Nested
  class Consultar {

    @Test
    void elPublicoNoVeBajasYElAdministradorSi() {
      when(puentes.findConCatalogoById(ID)).thenReturn(Optional.of(puente(false)));

      esError(() -> servicio().detalle(ID, false), HttpStatus.NOT_FOUND, "puente-no-encontrado");
      assertThat(servicio().detalle(ID, true).motivoBaja()).isEqualTo("Demolido");
    }

    @Test
    void listaPaginadoPorCodigo() {
      ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
      when(puentes.findAll(any(Specification.class), pagina.capture()))
          .thenReturn(new PageImpl<>(List.of(puente(true)), PageRequest.of(1, 5), 6));

      var resultado = servicio().listar("09", null, EstadoFiltro.SIN_EVALUAR, "rosas", false, 1, 5);

      assertThat(pagina.getValue().getSort().getOrderFor("codigo")).isNotNull();
      assertThat(resultado.contenido())
          .extracting(PuenteDTO::codigo)
          .containsExactly("GT-09-0901-0001");
      assertThat(resultado.pagina()).isEqualTo(1);
      assertThat(resultado.totalElementos()).isEqualTo(6);
      assertThat(resultado.totalPaginas()).isEqualTo(2);
    }

    @Test
    void rechazaPaginacionYBusquedaInvalidas() {
      PuenteService servicio = servicio();
      esError(
          () -> servicio.listar(null, null, null, null, false, -1, 20),
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida");
      esError(
          () -> servicio.listar(null, null, null, null, false, 0, 101),
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida");
      esError(
          () -> servicio.listar(null, null, null, null, false, 0, 0),
          HttpStatus.BAD_REQUEST,
          "paginacion-invalida");
      esError(
          () -> servicio.listar(null, null, null, "x".repeat(121), false, 0, 20),
          HttpStatus.BAD_REQUEST,
          "busqueda-muy-larga");
    }

    @Test
    void losCercanosVienenDeLaConsultaEspacial() {
      when(cercanos.buscar(14.8, -91.5, ID)).thenReturn(List.of(CERCANO));

      assertThat(servicio().cercanos(14.8, -91.5, ID)).containsExactly(CERCANO);
    }
  }
}
