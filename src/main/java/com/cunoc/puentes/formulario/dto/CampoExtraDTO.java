package com.cunoc.puentes.formulario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Campo adicional que se añade a una sección del formulario base (B2)")
public record CampoExtraDTO(
    @Schema(example = "datos_generales", description = "Sección base donde se muestra el campo")
        String seccion,
    @Schema(
            example = "estado_drenaje",
            description =
                "Clave única dentro de la sección: minúsculas, números y guion bajo, empieza por letra")
        String clave,
    @Schema(example = "Estado del drenaje", description = "Título visible en el formulario")
        String titulo,
    @Schema(
            example = "opcion",
            allowableValues = {
              "texto",
              "texto_largo",
              "numero",
              "entero",
              "si_no",
              "opcion",
              "fecha"
            })
        String tipo,
    @Schema(description = "Si es true, la inspección no se puede enviar sin este campo")
        Boolean obligatorio,
    @Schema(description = "Opciones permitidas; solo para el tipo opcion") List<String> opciones,
    @Schema(description = "Mínimo permitido; solo para numero y entero") BigDecimal minimo,
    @Schema(description = "Máximo permitido; solo para numero y entero") BigDecimal maximo,
    @Schema(example = "%", description = "Unidad de medida; solo para numero y entero")
        String unidad,
    @Schema(description = "Texto de ayuda que aparece bajo el campo") String ayuda) {}
