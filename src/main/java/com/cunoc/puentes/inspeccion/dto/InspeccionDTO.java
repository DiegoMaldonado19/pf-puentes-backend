package com.cunoc.puentes.inspeccion.dto;

import com.cunoc.puentes.indice.EstadoCondicion;
import com.cunoc.puentes.inspeccion.EstadoInspeccion;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InspeccionDTO(
    UUID id,
    PuenteResumenDTO puente,
    FormularioResumenDTO formulario,
    EstadoInspeccion estado,
    Long version,
    @JsonProperty("fecha_inspeccion") LocalDate fechaInspeccion,
    @JsonProperty("curso_id") UUID cursoId,
    @JsonProperty("autor_id") UUID autorId,
    @JsonProperty("dispositivo_id") String dispositivoId,
    @JsonProperty("creada_en_dispositivo") Instant creadaEnDispositivo,
    @JsonProperty("creado_en") Instant creadoEn,
    @JsonProperty("enviada_en") Instant enviadaEn,
    @JsonProperty("sincronizada_en") Instant sincronizadaEn,
    @JsonProperty("publicada_en") Instant publicadaEn,
    @JsonProperty("datos_permanentes_confirmados_en") Instant datosPermanentesConfirmadosEn,
    @JsonProperty("precargada_de_id") UUID precargadaDeId,
    @JsonProperty("gps_disponible") boolean gpsDisponible,
    Double latitud,
    Double longitud,
    @JsonProperty("distancia_a_puente_m") BigDecimal distanciaAPuenteM,
    @JsonProperty("advertencia_ubicacion") String advertenciaUbicacion,
    Map<String, Object> datos,
    List<AcompananteDTO> acompanantes,
    @JsonProperty("indice_condicion") BigDecimal indiceCondicion,
    @JsonProperty("estado_calculado") EstadoCondicion estadoCalculado,
    @JsonProperty("estado_confirmado") EstadoCondicion estadoConfirmado,
    @JsonProperty("justificacion_estado") String justificacionEstado,
    boolean superada,
    @JsonProperty("superada_por") UUID superadaPor,
    @JsonProperty("supersede_a_id") UUID supersedeAId,
    @JsonProperty("motivo_correccion") String motivoCorreccion,
    List<String> advertencias) {

  public InspeccionDTO conAdvertencias(List<String> extras) {
    return new InspeccionDTO(
        id,
        puente,
        formulario,
        estado,
        version,
        fechaInspeccion,
        cursoId,
        autorId,
        dispositivoId,
        creadaEnDispositivo,
        creadoEn,
        enviadaEn,
        sincronizadaEn,
        publicadaEn,
        datosPermanentesConfirmadosEn,
        precargadaDeId,
        gpsDisponible,
        latitud,
        longitud,
        distanciaAPuenteM,
        advertenciaUbicacion,
        datos,
        acompanantes,
        indiceCondicion,
        estadoCalculado,
        estadoConfirmado,
        justificacionEstado,
        superada,
        superadaPor,
        supersedeAId,
        motivoCorreccion,
        List.copyOf(extras == null ? List.of() : extras));
  }
}
