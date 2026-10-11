package com.cunoc.puentes.usuario;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

  // RN-USR-05, RN-USR-08: solo los servicios de usuario/ cambian el rol y el estado de la cuenta
  @Setter(AccessLevel.PACKAGE)
  @Enumerated(EnumType.STRING)
  private Rol rol;

  @Setter(AccessLevel.PACKAGE)
  @Enumerated(EnumType.STRING)
  private EstadoUsuario estado = EstadoUsuario.PENDIENTE;

  private Instant correoVerificadoEn;

  private String numeroColegiado;

  private Instant colegiadoVerificadoEn;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "colegiado_verificado_por")
  private Usuario colegiadoVerificadoPor;

  // RN-USR-07: el bloqueo por intentos fallidos lo maneja solo el servicio de inicio de sesión
  @Setter(AccessLevel.PACKAGE)
  private int intentosFallidos;

  @Setter(AccessLevel.PACKAGE)
  private Instant primerFalloEn;

  @Setter(AccessLevel.PACKAGE)
  private Instant bloqueadoHasta;

  // RN-USR-08: baja lógica con rastro; los tres van juntos y se limpian al reactivar
  private Instant desactivadoEn;

  /** Nulo si la baja la hizo el Sistema. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "desactivado_por")
  private Usuario desactivadoPor;

  private String motivoBaja;
}
