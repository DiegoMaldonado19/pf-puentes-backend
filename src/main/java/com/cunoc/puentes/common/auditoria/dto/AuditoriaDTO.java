package com.cunoc.puentes.common.auditoria.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Una fila de la bitácora tal como se guardó (RN-USR-09). Ya viene saneada: no se vuelve a
 * enmascarar.
 */
public record AuditoriaDTO(
    UUID id,
    @JsonProperty("usuario_id") UUID usuarioId,
    String accion,
    String entidad,
    @JsonProperty("entidad_id") UUID entidadId,
    @JsonProperty("valor_anterior") Map<String, Object> valorAnterior,
    @JsonProperty("valor_nuevo") Map<String, Object> valorNuevo,
    String motivo,
    @JsonProperty("origen_ip") String origenIp,
    @JsonProperty("correlacion_id") UUID correlacionId,
    @JsonProperty("creado_en") Instant creadoEn) {}
