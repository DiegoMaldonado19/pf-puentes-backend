package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.seguridad.TokenService;
import com.cunoc.puentes.usuario.dto.SesionDTO;
import org.springframework.stereotype.Component;

/** Arma la respuesta de sesión (token de acceso y datos del usuario) al iniciar o renovar. */
@Component
class EmisorDeAcceso {

  private final TokenService tokens;
  private final UsuarioMapper mapper;

  EmisorDeAcceso(TokenService tokens, UsuarioMapper mapper) {
    this.tokens = tokens;
    this.mapper = mapper;
  }

  SesionDTO emitir(Usuario cuenta) {
    return new SesionDTO(
        tokens.emitirAcceso(cuenta.getId(), cuenta.getRol()),
        SesionDTO.BEARER,
        tokens.vigencia().toSeconds(),
        mapper.aSesion(cuenta));
  }
}
