package com.cunoc.puentes.mantenimiento;

/** Mismo contrato de motivo/auditoría/seguridad, con origen PROGRAMADA y carrera contra inicio. */
class CancelacionOrdenTest extends DecisionConMotivoOrdenTest {
  @Override
  protected boolean esCancelacion() {
    return true;
  }
}
