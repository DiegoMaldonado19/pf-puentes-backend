package com.cunoc.puentes.common.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * DT-SEC-07: tope por IP y endpoint, en memoria, antes del JWT. Las reglas salen de {@code
 * sgp.limite-tasa.reglas} con la misma forma que las rutas públicas.
 */
final class LimitadorDeTasa extends OncePerRequestFilter {

  private static final AntPathMatcher RUTAS = new AntPathMatcher();

  private record Regla(String metodo, String patron, int maximo, Duration ventana) {

    boolean coincide(HttpServletRequest request) {
      return metodo.equalsIgnoreCase(request.getMethod()) && RUTAS.match(patron, ruta(request));
    }
  }

  private final List<Regla> reglas;
  private final Clock reloj;
  private final ContadorDeVentanaFija contador;
  private final RespuestasSeguridad respuestas;

  LimitadorDeTasa(List<String> declaraciones, Clock reloj, RespuestasSeguridad respuestas) {
    this.reglas = parsear(declaraciones);
    this.reloj = reloj;
    this.contador = new ContadorDeVentanaFija();
    this.respuestas = respuestas;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return reglas.isEmpty();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
      throws ServletException, IOException {
    Regla regla = coincidencia(request);
    if (regla == null) {
      cadena.doFilter(request, response);
      return;
    }
    String ip = ip(request);
    String clave = ip + " " + regla.metodo() + " " + regla.patron();
    ContadorDeVentanaFija.Decision decision =
        contador.registrar(clave, regla.maximo(), regla.ventana(), reloj.instant());
    if (!decision.admitida()) {
      respuestas.demasiadasSolicitudes(request, response, decision.reintentoSegundos());
      return;
    }
    cadena.doFilter(request, response);
  }

  private Regla coincidencia(HttpServletRequest request) {
    for (Regla regla : reglas) {
      if (regla.coincide(request)) {
        return regla;
      }
    }
    return null;
  }

  private static String ip(HttpServletRequest request) {
    String addr = request.getRemoteAddr();
    return StringUtils.hasText(addr) ? addr : "desconocida";
  }

  private static String ruta(HttpServletRequest request) {
    String path = request.getServletPath();
    String resto = request.getPathInfo();
    if (StringUtils.hasText(resto)) {
      path = path + resto;
    }
    return StringUtils.hasText(path) ? path : request.getRequestURI();
  }

  private static List<Regla> parsear(List<String> declaraciones) {
    List<Regla> leidas = new ArrayList<>();
    for (String cruda : declaraciones) {
      if (!StringUtils.hasText(cruda)) {
        continue;
      }
      leidas.add(regla(cruda.trim()));
    }
    return List.copyOf(leidas);
  }

  private static Regla regla(String declaracion) {
    String[] lado = declaracion.split("=", 2);
    if (lado.length != 2) {
      throw invalida(declaracion);
    }
    String[] ruta = lado[0].trim().split("\\s+", 2);
    String[] cupo = lado[1].trim().split("/", 2);
    if (ruta.length != 2 || cupo.length != 2) {
      throw invalida(declaracion);
    }
    String metodo = ruta[0].toUpperCase(Locale.ROOT);
    try {
      if (!metodoConocido(metodo)) {
        throw new IllegalArgumentException();
      }
      int maximo = Integer.parseInt(cupo[0]);
      Duration ventana = Duration.parse(cupo[1]);
      if (maximo < 1 || ventana.isZero() || ventana.isNegative()) {
        throw new IllegalArgumentException();
      }
      return new Regla(metodo, ruta[1], maximo, ventana);
    } catch (IllegalArgumentException | DateTimeException e) {
      throw invalida(declaracion, e);
    }
  }

  private static boolean metodoConocido(String metodo) {
    for (HttpMethod conocido : HttpMethod.values()) {
      if (conocido.name().equals(metodo)) {
        return true;
      }
    }
    return false;
  }

  private static IllegalArgumentException invalida(String declaracion) {
    return invalida(declaracion, null);
  }

  private static IllegalArgumentException invalida(String declaracion, Exception causa) {
    return new IllegalArgumentException(
        "sgp.limite-tasa.reglas: se esperaba 'METODO /ruta=peticiones/ventana' y llegó '"
            + declaracion
            + "'",
        causa);
  }
}
