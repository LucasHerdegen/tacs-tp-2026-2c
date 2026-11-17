---
name: feature-testing
description: >-
  Guides behavior-driven testing across backend layers (domain, services, WebMvcTest,
  MongoDB concurrency, OpenTelemetry) and frontend flows. Use when adding or modifying
  endpoints, use cases, domain rules, or UI features.
---

# Skill: Desarrollo y Testing Guiado por Comportamiento

## Propósito
Asegurar que cada nueva funcionalidad, endpoint REST, comando de Telegram, regla de negocio o flujo de interfaz cuente con validación automatizada antes de darse por finalizada, previniendo regresiones.

## Cuándo activar esta Skill
- Al crear o modificar un caso de uso en el dominio, un servicio (`Service`), un controller (`Controller`), un handler de Telegram, o un flujo de UI en `frontend/`.

## Instrucciones paso a paso

### 1. Análisis de comportamiento esperado
Antes de implementar o modificar código productivo, definí:
- Precondiciones, invariantes de dominio y permisos (organizador, participante, usuario autenticado, admin).
- Happy Path y todos los Failing Paths esperados (validación `400 ProblemDetail`, no autenticado `401`, sin permisos `403`, inexistente `404`, conflicto de estado/cupo/concurrencia `409`, indisponibilidad externa `503`).

### 2. Diseño de tests en Backend (`backend/`)
Nombrá cada test siguiendo el patrón `metodo_escenario_resultadoEsperado` con `@DisplayName` descriptivo en español, eligiendo el tipo de test adecuado por capa:

1. **Dominio (`domain/`):** Tests unitarios puros (JUnit 5 + AssertJ) probando transiciones de estado (`PROPUESTA`, `CONFIRMADA`, `REPROGRAMADA`, `CANCELADA`, `FINALIZADA`), rangos y validaciones sin mocks ni Spring.
2. **Servicios y Jobs (`services/implem/`, `scheduling/`):** Unitarios con JUnit 5 + Mockito. Mockeá siempre `ProveedorClima`, repositorios y API de Telegram.
3. **Capa HTTP y Seguridad (`controllers/`):** `@WebMvcTest(controllers = ...)` + `@MockitoBean` + `MockMvc` (importando `SecurityConfig`, `JwtServiceImplem`, `TimeConfig`). Verificá:
   - `401 Unauthorized` sin JWT.
   - `403 Forbidden` por rol (`USER` en endpoints `ADMIN`) o por autorización (ej. no organizador / no participante).
   - `400` con estructura `ProblemDetail` (RFC 7807) ante DTOs o rangos inválidos.
4. **Observabilidad (`observabilidad/`):** Si tocás spans o métricas, usá `OpenTelemetrySdk` + `InMemorySpanExporter` + `InMemoryMetricReader` (ver `ObservabilidadBusquedasTest`), verificando nombres de spans, `StatusCode.ERROR` ante fallos y ausencia de PII/texto libre.
5. **Persistencia / Concurrencia (`@DataMongoTest`):** Los tests `@DataMongoTest` requieren un MongoDB real corriendo en `localhost:27017` (o `${MONGODB_URI}`) para validar índices únicos, queries de `MongoTemplate` y colisiones reales de `@Version`. Levantalo con `docker compose up -d mongodb` antes de ejecutarlos.

### 3. Implementación y ejecución dirigida
1. Escribí los tests del Happy Path y Failing Paths.
2. Implementá el código productivo mínimo y limpio necesario.
3. Ejecutá desde `backend/` la clase de test puntual:
   ```powershell
   .\mvnw.cmd test -Dtest=NombreDeLaClaseTest
   ```
   *(En Linux/macOS: `./mvnw test -Dtest=NombreDeLaClaseTest`)*.
4. Si también modificaste `frontend/`, verificá tipos, lint y el manejo de errores (`ApiError` / `extractFieldErrors`) con `npm run lint` y `npm run build` desde `frontend/`.

## Formato de Reporte
Al entregar cambios, incluí un breve resumen con:
- **Funcionalidad implementada:** [Descripción breve]
- **Casos de prueba cubiertos:** [Happy Path y Failing Paths validados]
- **Verificación ejecutada:** [Comandos corridos y resultado]
