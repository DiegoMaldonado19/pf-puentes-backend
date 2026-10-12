package com.cunoc.puentes.usuario;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

  // lower(...) en ambos lados: es la expresión de usuario_correo_uk, así que usa su índice
  @Query("SELECT u FROM Usuario u WHERE lower(u.correo) = lower(:correo)")
  Optional<Usuario> buscarPorCorreo(String correo);

  @Query("SELECT count(u) > 0 FROM Usuario u WHERE lower(u.correo) = lower(:correo)")
  boolean existePorCorreo(String correo);

  // Una consulta por la clave primaria: el filtro JWT la hace en cada petición
  @Query(
      "SELECT u.rol FROM Usuario u WHERE u.id = :id"
          + " AND u.estado = com.cunoc.puentes.usuario.EstadoUsuario.ACTIVO")
  Optional<Rol> buscarRolActivo(UUID id);
}
