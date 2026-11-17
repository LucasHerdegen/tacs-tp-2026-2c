# Reglas del Frontend — React / TypeScript / Vite (`frontend/`)

Estas reglas complementan al [AGENTS.md raíz](../AGENTS.md) cuando trabajes dentro de `frontend/`.

## Stack y Organización

- **Stack:** React 19, TypeScript estricto, Vite, Tailwind CSS v4, React Router DOM, Leaflet (`react-leaflet`) y `@opentelemetry/sdk-trace-web`.
- **Estructura por feature:** `src/features/<feature>/` (`activities`, `admin`, `auth`, `dashboard`, `notifications`, `voting`) con sus módulos `*Api.ts`, `types.ts`, `components/` y `views/`. Los elementos transversales viven en `src/components/`, `src/hooks/`, `src/layouts/`, `src/lib/` y `src/utils/`.

## Convenciones y Estilo

- **Idioma y formato TypeScript:** Los textos de interfaz, etiquetas, mensajes de validación y notificaciones al usuario deben estar en **español (Argentina)**. El código TypeScript/TSX sigue la convención estándar de JS/TS/ESLint (llaves en la misma línea K&R, tipos estrictos sin `any`, sin código comentado ni `console.log` residuales).
- **Identificadores MongoDB (`string`):** Los `id` de actividades, usuarios, votaciones y notificaciones son `string` (`ObjectId`). Nunca los conviertas con `Number(id)`.
- **Manejo de errores HTTP (`ProblemDetail`):** Canalizá las llamadas a la API a través de `apiRequest` (`src/lib/api.ts`) y usá `ApiError` + `extractFieldErrors` para mostrar mensajes claros de validación por campo o errores generales del backend. No muestres errores crudos del servidor ni reemplaces errores `5xx`/red por mensajes falsos de negocio (ej. "No encontrado").
- **Formularios e inputs numéricos:** Permití que el usuario borre completamente un input numérico durante la edición (evitando forzar `"0"` adelante, como `"04"`), y mostrá `"No definido"` cuando una métrica o límite opcional sea `null`/`undefined` en vez de `0`.
- **Observabilidad Web (`src/telemetry.ts`):** Mantené `FetchInstrumentation` estrictamente acotada mediante `ignoreUrls: [/^(?!.*\/api\/actividades\?).*$/]` para no generar spans innecesarios ni problemas de CORS en otras pantallas (ADR 13).

## Verificación Obligatoria

Después de cualquier cambio en `frontend/`, ejecutá desde `frontend/`:
- `npm run lint`
- `npm run build` (`tsc -b && vite build`)
