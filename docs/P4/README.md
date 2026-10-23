# Manual Técnico de P4 (Backend) — Motor de Formularios y Versionado

Implementación: `com.cunoc.puentes.formulario`. OpenAPI: `/api/docs`, etiqueta **Formularios**.  
Reglas de negocio asociadas: **RN-FRM-01** a **RN-FRM-08**.  
Directrices técnicas: **DT-ARQ-01..05**, **DT-BE-01..05**, **DT-BE-10**, **DT-BD-02..03**, **DT-CAL-08..09**.

---

## 1. Responsabilidad del Módulo y Límites

El módulo `formulario/` es el **propietario absoluto de la estructura, validación, metadatos y versionado del formulario de evaluación de puentes**.  
**No es propietario de los datos de inventario** (pertenecen a P3 en `puente/`) ni de los **datos capturados de la inspección** (pertenecen a P5 en `inspeccion/`).

```
┌─────────────────────────────────────────────────────────────┐
│                       P4: FORMULARIO                        │
│  - Define estructura (JSON Schema Draft 7 / 2020-12)        │
│  - Valida autoritativamente con networknt validator         │
│  - Gestiona ciclo inmutable: BORRADOR -> ACTIVA -> RETIRADA │
│  - Mapea equivalencias entre versiones históricas           │
└─────────────────────────────────────────────────────────────┘
         ▲                                       ▲
         │ Consulta esquema activo               │ Valida datos antes
         │ (Contrato 1)                          │ de persistir
┌─────────────────────────┐             ┌─────────────────────┐
│  P6: OFFLINE / SYNC     │             │  P5: INSPECCIONES   │
│  - Almacena localmente  │             │  - Captura y guarda │
│    esquema en Dexie     │             │    en datos (JSONB) │
└─────────────────────────┘             └─────────────────────┘
```

---

## 2. Modelo de Datos y Estados

El DDL reside en `V2026_10_04_2005__crear_formulario.sql`:

```sql
CREATE TABLE formulario_version (
  id UUID PRIMARY KEY,
  codigo VARCHAR(20) NOT NULL UNIQUE,
  esquema JSONB NOT NULL,
  mapeo JSONB,
  estado VARCHAR(10) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR', 'ACTIVA', 'RETIRADA')),
  publicada_en TIMESTAMPTZ,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (estado = 'BORRADOR' OR publicada_en IS NOT NULL)
);

CREATE UNIQUE INDEX formulario_version_activa_uk 
  ON formulario_version (estado) WHERE estado = 'ACTIVA';
```

### Máquina de Estados de la Versión (`EstadoFormulario`)

| Estado | Significado | Mutabilidad | ¿Inspecciones Nuevas? | ¿Consulta Histórica? |
|---|---|:---:|:---:|:---:|
| `BORRADOR` | Versión en diseño/edición por el Administrador. | Mutable | ❌ No | ❌ No |
| `ACTIVA` | Versión oficial vigente para todo el sistema (única). | 🔒 Inmutable | ✅ Sí | ✅ Sí |
| `RETIRADA` | Versión histórica sustituida por una más reciente. | 🔒 Inmutable | ❌ No | ✅ Sí (RN-FRM-05) |

* **RN-FRM-03 (Inmutabilidad):** Una vez publicada (`ACTIVA`), no se permiten actualizaciones sobre el JSON Schema. Toda modificación requiere clonar un nuevo `BORRADOR`.
* **RN-FRM-04 (Unicidad activa):** Garantizada por el índice único parcial condicional `formulario_version_activa_uk`.

---

## 3. Endpoints REST (`FormularioController`)

Ruta base bajo `/api/v1/formularios`.

| Método | Ruta | Rol Requerido | Descripción |
|---|---|---|---|
| `GET` | `/activo` | Autenticado (`ESTUDIANTE`, `CATEDRATICO`, `ADMINISTRADOR`) | Devuelve el esquema activo para levantar inspecciones y cachear offline (`RN-FRM-04`). |
| `GET` | `/versiones` | `ADMINISTRADOR`, `CATEDRATICO` | Lista paginada del historial de versiones y estados. |
| `GET` | `/versiones/{codigo}` | Autenticado | Devuelve el esquema de una versión específica para renderizar inspecciones históricas (`RN-FRM-05`). |
| `POST` | `/versiones` | `ADMINISTRADOR` | Crea un nuevo borrador de versión (`EstadoFormulario.BORRADOR`). |
| `PUT` | `/versiones/{id}` | `ADMINISTRADOR` | Modifica el JSON Schema de un borrador existente. |
| `POST` | `/versiones/{id}/publicar` | `ADMINISTRADOR` | Publica la versión: pasa a `ACTIVA`, retira la activa anterior y registra el `mapeo` de campos (`RN-FRM-06`). |

---

## 4. Validación Autoritativa en Servidor (DT-BE-04)

P4 expone el servicio de validación utilizado obligatoriamente por P5 al crear o actualizar una inspección:

```java
public interface FormularioValidacionService {
  /**
   * Valida autoritativamente el contenido capturado contra el esquema oficial.
   * @param versionId Identificador de la versión del formulario.
   * @param datosPayload Respuestas capturadas en la inspección (JSONB).
   * @throws ValidacionEsquemaException si existen errores de tipo, requeridos o rangos.
   */
  void validar(UUID versionId, Map<String, Object> datosPayload);
}
```

* **Librería estándar:** `com.networknt:json-schema-validator`.
* **Formato de Error ProblemDetail (RFC 7807):**
  En caso de fallo de validación, la respuesta HTTP es `422 Unprocessable Entity` con la lista de campos y errores:
  ```json
  {
    "type": "https://sgp.cunoc.edu.gt/errores/validacion-esquema",
    "title": "Error de validación contra JSON Schema",
    "status": 422,
    "detail": "El contenido de la inspección no cumple con las reglas del formulario activo.",
    "instance": "/api/v1/inspecciones",
    "errores": [
      {
        "campo": "subestructura.estribo_entrada.cortina_superior.danos.grietas",
        "mensaje": "El valor 5 está fuera del rango permitido [0, 4]"
      }
    ]
  }
  ```

---

## 5. Mapeo y Trazabilidad con otros Módulos

* **P3 (Inventario):** Pre-carga de atributos permanentes en Secciones 1 y 2 identificados por `x-permanente: true`. Las claves coinciden con `PuenteDTO` (`codigo`, `nombre`, `ruta`, `anio_construccion`, `latitud`, `longitud`, `utm`).
* **P5 (Inspecciones):** Cada entidad `Inspeccion` guarda obligatoriamente `formulario_version_id` (FK inmutable) y valida `datos` contra este motor.
* **P8 (Índice de Condición):** `ConfiguracionPesosService` se vincula a una `formulario_version_id` y extrae los elementos de daño identificados con `x-dano: true` (Contrato 1).
* **P1, P7, P9:** Usan la sintaxis canónica de `elemento_ref` (Contrato 2) para anclar fotos, observaciones académicas e hilos de discusión.
