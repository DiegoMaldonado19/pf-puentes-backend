package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends EntidadBase {

  private String correo;

  private String nombre;

  private String contrasenaHash;

  @Enumerated(EnumType.STRING)
  private Rol rol;

  @Enumerated(EnumType.STRING)
  private EstadoUsuario estado = EstadoUsuario.PENDIENTE;

  private Instant correoVerificadoEn;

  private String numeroColegiado;

  private Instant colegiadoVerificadoEn;

  private int intentosFallidos;

  private Instant primerFalloEn;

  private Instant bloqueadoHasta;
}
