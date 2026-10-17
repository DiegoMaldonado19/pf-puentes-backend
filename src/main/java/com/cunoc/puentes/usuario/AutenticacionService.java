package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.common.seguridad.TokenService;
import com.cunoc.puentes.usuario.dto.SesionDTO;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Inicio de sesión (RN-USR-01, RN-USR-07). No es transaccional a propósito: la comprobación BCrypt
 * tarda y no debe tener una conexión ni una fila bloqueadas mientras corre.
 */
@Service
public class AutenticacionService {

  /** El token de renovación en claro solo existe aquí y en la cookie; la base guarda su hash. */
  record SesionIniciada(SesionDTO sesion, String tokenRenovacion, Duration vigenciaRenovacion) {

    // DT-SEC-12: el toString por defecto imprimiría el token de renovación
    @Override
    public String toString() {
      return "SesionIniciada[" + sesion + "]";
    }
  }

  private static final Logger log = LoggerFactory.getLogger(AutenticacionService.class);
  private static final int BYTES_MAXIMOS_BCRYPT = 72;

  private final UsuarioRepository usuarios;
  private final TokenRenovacionRepository renovaciones;
  private final ControlDeIntentos intentos;
  private final TokenService tokens;
  private final PasswordEncoder codificador;
  private final PoliticaDeBloqueo politica;
  private final UsuarioMapper mapper;
  private final Duration vigenciaRenovacion;
  private final String hashFicticio;

  AutenticacionService(
      UsuarioRepository usuarios,
      TokenRenovacionRepository renovaciones,
      ControlDeIntentos intentos,
      TokenService tokens,
      PasswordEncoder codificador,
      PoliticaDeBloqueo politica,
      UsuarioMapper mapper,
      @Value("${sgp.jwt.vigencia-renovacion:P7D}") Duration vigenciaRenovacion) {
    this.usuarios = usuarios;
    this.renovaciones = renovaciones;
    this.intentos = intentos;
    this.tokens = tokens;
    this.codificador = codificador;
    this.politica = politica;
    this.mapper = mapper;
    this.vigenciaRenovacion = vigenciaRenovacion;
    // Con un correo desconocido se compara contra esto: tarda lo mismo que con uno conocido
    this.hashFicticio = codificador.encode("contrasena-ficticia-que-nadie-tiene");
  }

  SesionIniciada iniciarSesion(String correo, String contrasena) {
    Optional<Usuario> encontrada = usuarios.buscarPorCorreo(correo);
    boolean coincide =
        coincide(contrasena, encontrada.map(Usuario::getContrasenaHash).orElse(hashFicticio));
    if (encontrada.isEmpty() || !coincide) {
      encontrada.ifPresent(this::registrarFallo);
      throw credencialesInvalidas();
    }

    // Solo con la contraseña correcta se dice algo del estado de la cuenta
    Usuario cuenta = encontrada.get();
    Instant ahora = Instant.now();
    verificarQuePuedeEntrar(cuenta, ahora);
    if (tieneRastroDeFallos(cuenta)) {
      intentos.reiniciar(cuenta.getId());
    }
    return emitirSesion(cuenta, ahora);
  }

  private void registrarFallo(Usuario cuenta) {
    // Mientras dura el bloqueo no se cuenta ni se extiende: el atacante no gana nada insistiendo
    if (PoliticaDeBloqueo.estaBloqueada(cuenta.getBloqueadoHasta(), Instant.now())) {
      return;
    }
    var resultado = intentos.registrarFallo(cuenta.getId());
    if (resultado.bloqueoNuevo()) {
      log.warn("Cuenta {} bloqueada hasta {}", cuenta.getId(), resultado.bloqueadoHasta());
    }
  }

  private void verificarQuePuedeEntrar(Usuario cuenta, Instant ahora) {
    switch (cuenta.getEstado()) {
      case PENDIENTE ->
          throw cuentaNoHabilitada(
              "cuenta-pendiente",
              "Cuenta pendiente de activación",
              "Tu cuenta aún no está activa.");
      case INACTIVO ->
          throw cuentaNoHabilitada(
              "cuenta-inactiva", "Cuenta inactiva", "Tu cuenta fue dada de baja.");
      case ACTIVO -> {
        if (PoliticaDeBloqueo.estaBloqueada(cuenta.getBloqueadoHasta(), ahora)) {
          long minutos = PoliticaDeBloqueo.minutosRestantes(cuenta.getBloqueadoHasta(), ahora);
          throw cuentaNoHabilitada(
              "cuenta-bloqueada",
              "Cuenta bloqueada temporalmente",
              "Demasiados intentos fallidos. Vuelve a intentarlo en %d minuto%s."
                  .formatted(minutos, minutos == 1 ? "" : "s"));
        }
      }
    }
  }

  private static boolean tieneRastroDeFallos(Usuario cuenta) {
    return cuenta.getIntentosFallidos() > 0
        || cuenta.getPrimerFalloEn() != null
        || cuenta.getBloqueadoHasta() != null;
  }

  private SesionIniciada emitirSesion(Usuario cuenta, Instant ahora) {
    String renovacion = TokenOpaco.generar();
    TokenRenovacion fila = new TokenRenovacion();
    fila.setUsuario(usuarios.getReferenceById(cuenta.getId()));
    fila.setTokenHash(TokenOpaco.hash(renovacion));
    fila.setExpiraEn(ahora.plus(vigenciaRenovacion));
    renovaciones.save(fila);

    SesionDTO sesion =
        new SesionDTO(
            tokens.emitirAcceso(cuenta.getId(), cuenta.getRol()),
            SesionDTO.BEARER,
            tokens.vigencia().toSeconds(),
            mapper.aSesion(cuenta));
    return new SesionIniciada(sesion, renovacion, vigenciaRenovacion);
  }

  // BCrypt trunca (o rechaza) más de 72 bytes: eso nunca puede ser una contraseña válida
  private boolean coincide(String contrasena, String hash) {
    String candidata =
        contrasena.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_BCRYPT ? "" : contrasena;
    return codificador.matches(candidata, hash);
  }

  // Un solo mensaje para correo desconocido y contraseña incorrecta (RN-USR-01, DT-SEC-12)
  private static NegocioException credencialesInvalidas() {
    return new NegocioException(
        HttpStatus.UNAUTHORIZED,
        "credenciales-invalidas",
        "Credenciales inválidas",
        "El correo o la contraseña no son correctos.");
  }

  private static NegocioException cuentaNoHabilitada(String tipo, String titulo, String detalle) {
    return new NegocioException(HttpStatus.FORBIDDEN, tipo, titulo, detalle);
  }
}
