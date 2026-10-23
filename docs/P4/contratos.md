# Contratos Técnicos de Integración: P4 (Formularios)

Este documento formaliza los contratos que **P4 expone a los demás módulos del sistema**, establecidos como parte de la ruta crítica del proyecto SGP.

---

## 🔒 Contrato 1: Especificación del JSON Schema y Metadatos de Negocio

* **Dueño:** P4 (Motor de Formularios y Versionado)
* **Consumidores:** P5 (Inspecciones), P6 (Offline / IndexedDB), P8 (Índice de Condición y Pesos)
* **Especificación Base:** JSON Schema Draft 7 / 2020-12
* **Estado:** 🔒 CONGELADO

### Metadatos Extendidos (`x-...`)

Para que los módulos consumidores no dependan de lógica cableada en código, el JSON Schema incluye los siguientes metadatos declarativos:

| Atributo | Tipo | Uso / Propósito | Módulo Consumidor |
|---|---|---|---|
| `x-permanente` | `boolean` | Indica que el campo pertenece al activo físico (puente) y debe pre-cargarse automáticamente. **En una inspección nueva no se pide reescribir.** | P3, P5 |
| `x-dano` | `boolean` | Indica que es una evaluación de daño en escala obligatoria entera `0` a `4`. **Nunca se precarga** (`RN-INS-10`). | P5, P8 |
| `x-grupo` | `string` | Agrupa visualmente elementos estructurales para habilitar el botón "Sin daños en este elemento" (`RN-INS-11`). | P4, P5 |
| `x-elemento-ref` | `string` | Ruta canónica normalizada del campo para anclajes (ver Contrato 2). | P1, P7, P8, P9 |
| `x-orden` | `integer` | Posición secuencial de renderizado dentro de la sección o grupo. | P4 |
| `x-unidad` | `string` | Unidad de medida para campos numéricos (`m`, `m²`, `años`, `%`). | P4 |

### Estructura Canónica de un Campo de Daño (IC)

```json
{
  "type": "integer",
  "minimum": 0,
  "maximum": 4,
  "title": "Grietas en cortina superior",
  "description": "0=Sin daño, 1=Incipiente, 2=Moderado, 3=Severo, 4=Falla crítica",
  "x-dano": true,
  "x-grupo": "estribo_entrada",
  "x-elemento-ref": "subestructura.estribo_entrada.cortina_superior.danos.grietas",
  "x-orden": 1
}
```

### Estructura Canónica de un Campo Permanente (Inventario P3)

```json
{
  "type": "string",
  "title": "Código INE del Puente",
  "readOnly": true,
  "x-permanente": true,
  "x-fuente": "puente.codigo",
  "x-orden": 1
}
```

---

## 🔒 Contrato 2: Formato Canónico de `elemento_ref`

* **Dueño:** P4 (Motor de Formularios y Versionado)
* **Consumidores:** P1 (Fotos y Documentos), P7 (Revisión Académica), P8 (Índice de Condición), P9 (Foro de Discusión)
* **Estado:** 🔒 CONGELADO

### Definición y Sintaxis
`elemento_ref` es la **clave semántica inmutable** que identifica unívocamente una parte física o pregunta dentro del puente inspeccionado, independientemente de la versión gráfica del formulario.

Sintaxis en kebab-case/snake_case con notación por puntos:
```
<seccion>.<componente>[.<subcomponente>].<campo_o_dano>
```

### Ejemplos Oficiales de Rutas

| `elemento_ref` | Sección SIECA | Consumidor Típico |
|---|---|---|
| `identificacion.codigo` | Sección 1: Identificación | P3 (Inventario) |
| `subestructura.estribo_entrada.cortina.grietas` | Sección 4: Sub-estructura | P1 (Foto anclada), P8 (IC) |
| `superestructura.vigas.viga_1.deformacion` | Sección 5: Super-estructura | P7 (Observación docente), P8 (IC) |
| `cauce.socavacion_estribos` | Sección 7: Cauce | P8 (Anulación crítica IC: Socavación) |
| `no_estructurales.barandas.corrosion` | Sección 6: No estructurales | P9 (Comentario de foro) |

### Reglas de Uso para Otros Módulos:
1. **P1 (Fotos):** Cada fotografía capturada en campo puede asociarse opcionalmente a un `elemento_ref` para mostrarse en miniatura al lado del campo en la revisión.
2. **P7 (Revisión Docente):** Los catedráticos dejan observaciones puntuales ancladas al `elemento_ref` de la falla observada.
3. **P8 (IC):** La matriz de ponderaciones mapea un peso `BigDecimal` directamente a un `elemento_ref` de daño (`RN-IC-03`).
4. **P9 (Foro):** Las discusiones técnicas pueden originarse ancladas a un elemento específico del puente.
