package com.cunoc.puentes.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstadoCuentaServiceTest {

  private static final UUID ID = UUID.fromString("0192f5a0-0000-7000-8000-000000000002");

  @Mock UsuarioRepository repositorio;
  @InjectMocks EstadoCuentaService servicio;

  @Test
  void devuelveElRolDeUnaCuentaActiva() {
    given(repositorio.buscarRolActivo(ID)).willReturn(Optional.of(Rol.CATEDRATICO));

    assertThat(servicio.rolSiEstaActiva(ID)).contains(Rol.CATEDRATICO);
  }

  @Test
  void noDevuelveRolSiLaCuentaNoEstaActivaONoExiste() {
    given(repositorio.buscarRolActivo(ID)).willReturn(Optional.empty());

    assertThat(servicio.rolSiEstaActiva(ID)).isEmpty();
  }
}
