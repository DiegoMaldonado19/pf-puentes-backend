package com.cunoc.puentes.usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

  // lower(...) en ambos lados: es la expresión de usuario_correo_uk, así que usa su índice
  @Query("SELECT u FROM Usuario u WHERE lower(u.correo) = lower(:correo)")
  Optional<Usuario> buscarPorCorreo(String correo);

  @Query("SELECT count(u) > 0 FROM Usuario u WHERE lower(u.correo) = lower(:correo)")
  boolean existePorCorreo(String correo);

  boolean existsByRol(Rol rol);

  /**
   * RN-USR-07, en una sola sentencia atómica (no leer-modificar-escribir): suma un intento, o
   * reinicia la ventana si el primer fallo ya salió de ella, y bloquea al llegar a {@code maximo}.
   * Limpia el contexto para que lo siguiente que se lea sea lo que quedó guardado.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE Usuario u SET
        u.intentosFallidos = CASE
          WHEN u.primerFalloEn IS NULL OR u.primerFalloEn <= :inicioVentana THEN 1
          ELSE u.intentosFallidos + 1 END,
        u.primerFalloEn = CASE
          WHEN u.primerFalloEn IS NULL OR u.primerFalloEn <= :inicioVentana THEN :ahora
          ELSE u.primerFalloEn END,
        u.bloqueadoHasta = CASE
          WHEN (CASE
                  WHEN u.primerFalloEn IS NULL OR u.primerFalloEn <= :inicioVentana THEN 1
                  ELSE u.intentosFallidos + 1 END) >= :maximo THEN :hasta
          ELSE u.bloqueadoHasta END,
        u.actualizadoEn = :ahora
      WHERE u.id = :id
      """)
  int registrarFallo(UUID id, Instant ahora, Instant inicioVentana, int maximo, Instant hasta);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE Usuario u SET u.intentosFallidos = 0, u.primerFalloEn = NULL,
        u.bloqueadoHasta = NULL, u.actualizadoEn = :ahora
      WHERE u.id = :id
      """)
  int reiniciarIntentos(UUID id, Instant ahora);

  // Una consulta por la clave primaria: el filtro JWT la hace en cada petición
  @Query(
      "SELECT u.rol FROM Usuario u WHERE u.id = :id"
          + " AND u.estado = com.cunoc.puentes.usuario.EstadoUsuario.ACTIVO")
  Optional<Rol> buscarRolActivo(UUID id);
}
