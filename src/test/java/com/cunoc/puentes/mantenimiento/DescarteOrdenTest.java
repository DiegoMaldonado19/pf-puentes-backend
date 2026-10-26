package com.cunoc.puentes.mantenimiento;

/** Contrato de descarte conservado y compartido con cancelación. */
class DescarteOrdenTest extends DecisionConMotivoOrdenTest {
  @Override
  protected boolean esCancelacion() {
    return false;
  }
}
