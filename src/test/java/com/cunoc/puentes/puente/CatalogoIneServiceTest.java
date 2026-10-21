package com.cunoc.puentes.puente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.puente.dto.DepartamentoDTO;
import com.cunoc.puentes.puente.dto.MunicipioDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CatalogoIneServiceTest {

  @Mock DepartamentoRepository departamentos;
  @Mock MunicipioRepository municipios;
  @Spy CatalogoIneMapper mapper = new CatalogoIneMapperImpl();
  @InjectMocks CatalogoIneService servicio;

  @Test
  void listaLosDepartamentosEnElOrdenDelRepositorio() {
    when(departamentos.findAllByOrderByNombreAsc())
        .thenReturn(List.of(Catalogo.departamento("16", "Alta Verapaz"), Catalogo.QUETZALTENANGO));

    assertThat(servicio.listarDepartamentos())
        .containsExactly(
            new DepartamentoDTO("16", "Alta Verapaz"), new DepartamentoDTO("09", "Quetzaltenango"));
  }

  @Test
  void listaLosMunicipiosDelDepartamento() {
    when(departamentos.existsById("09")).thenReturn(true);
    when(municipios.findByDepartamentoCodigoOrderByNombreAsc("09"))
        .thenReturn(List.of(Catalogo.municipio("0913", "Almolonga"), Catalogo.MUNICIPIO_XELA));

    assertThat(servicio.listarMunicipios("09"))
        .containsExactly(
            new MunicipioDTO("0913", "Almolonga", "09"),
            new MunicipioDTO("0901", "Quetzaltenango", "09"));
  }

  @Test
  void unDepartamentoInexistenteEs404() {
    when(departamentos.existsById("99")).thenReturn(false);

    assertThatThrownBy(() -> servicio.listarMunicipios("99"))
        .isInstanceOfSatisfying(
            NegocioException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(departamentos).existsById("99");
    verifyNoInteractions(municipios);
  }
}
