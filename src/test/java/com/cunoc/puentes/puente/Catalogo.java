package com.cunoc.puentes.puente;

import org.springframework.test.util.ReflectionTestUtils;

/** Entidades del catálogo INE para pruebas unitarias; su constructor es protegido (ADR 0012). */
final class Catalogo {

  static final Departamento QUETZALTENANGO = departamento("09", "Quetzaltenango");
  static final Municipio MUNICIPIO_XELA = municipio("0901", "Quetzaltenango");

  private Catalogo() {}

  static Departamento departamento(String codigo, String nombre) {
    Departamento departamento = new Departamento();
    ReflectionTestUtils.setField(departamento, "codigo", codigo);
    ReflectionTestUtils.setField(departamento, "nombre", nombre);
    return departamento;
  }

  /** Municipio de Quetzaltenango (09). */
  static Municipio municipio(String codigo, String nombre) {
    return municipio(codigo, nombre, QUETZALTENANGO);
  }

  static Municipio municipio(String codigo, String nombre, Departamento departamento) {
    Municipio municipio = new Municipio();
    ReflectionTestUtils.setField(municipio, "codigo", codigo);
    ReflectionTestUtils.setField(municipio, "nombre", nombre);
    ReflectionTestUtils.setField(municipio, "departamento", departamento);
    return municipio;
  }
}
