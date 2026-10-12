package com.cunoc.puentes.common.auditoria;

import java.util.Set;

/**
 * Catálogo oficial de acciones auditables. Un módulo que necesite otra la agrega aquí y avisa al
 * equipo, para que la bitácora se lea igual en todas partes.
 */
public final class Acciones {

  public static final String CREAR = "CREAR";
  public static final String MODIFICAR = "MODIFICAR";
  public static final String CAMBIAR_ESTADO = "CAMBIAR_ESTADO";
  public static final String DAR_DE_BAJA = "DAR_DE_BAJA";
  public static final String REACTIVAR = "REACTIVAR";
  public static final String MODERAR = "MODERAR";
  public static final String INVITAR = "INVITAR";
  public static final String REVOCAR_INVITACION = "REVOCAR_INVITACION";
  public static final String VERIFICAR_COLEGIADO = "VERIFICAR_COLEGIADO";
  public static final String BLOQUEAR_CUENTA = "BLOQUEAR_CUENTA";

  public static final Set<String> CATALOGO =
      Set.of(
          CREAR,
          MODIFICAR,
          CAMBIAR_ESTADO,
          DAR_DE_BAJA,
          REACTIVAR,
          MODERAR,
          INVITAR,
          REVOCAR_INVITACION,
          VERIFICAR_COLEGIADO,
          BLOQUEAR_CUENTA);

  private Acciones() {}
}
